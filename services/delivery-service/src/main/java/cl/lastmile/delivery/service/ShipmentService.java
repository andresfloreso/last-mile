package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import cl.lastmile.delivery.dto.DeliveryDtos.*;
import cl.lastmile.delivery.geo.GeocodingService;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeclaredWindow;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.ShipmentDefaults;
import cl.lastmile.delivery.repository.CustomerRepository;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.repository.ShipmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final CustomerRepository customerRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final GeocodingService geocodingService;
    private final TimeWindowLearningService learningService;

    /** Peso y volumen de un envío nuevo que no los informa (HU-29). */
    @Value("${shipments.default-weight-kg:" + ShipmentDefaults.WEIGHT_KG + "}")
    private double defaultWeightKg = ShipmentDefaults.WEIGHT_KG;
    @Value("${shipments.default-volume-m3:" + ShipmentDefaults.VOLUME_M3 + "}")
    private double defaultVolumeM3 = ShipmentDefaults.VOLUME_M3;

    @Transactional
    public CustomerResponse createOrGetCustomer(CustomerRequest req) {
        Customer.BusinessType businessType = req.businessType() == null || req.businessType().isBlank()
                ? null
                : RequestValues.enumValue(Customer.BusinessType.class, req.businessType(), "businessType");
        DeclaredWindow declaredWindow = req.declaredWindow().validate();

        if (req.externalCode() != null) {
            var existing = customerRepository.findByExternalCode(req.externalCode());
            if (existing.isPresent()) {
                // Reimportar la planilla actualiza el tipo y la modulación si vienen informados;
                // si la fila no los trae, se conservan los que ya tenía el local.
                Customer customer = existing.get();
                if (businessType != null) customer.setBusinessType(businessType);
                if (!declaredWindow.isEmpty()) customer.applyDeclaredWindow(declaredWindow);
                // Un local "por revisar" se corrige reimportando la fila con latitud/longitud.
                if (customer.needsGeocodeReview() && req.latitude() != null && req.longitude() != null) {
                    customer.relocate(req.latitude(), req.longitude());
                }
                return CustomerResponse.from(customer);
            }
        }

        // Prioridad de precisión: si la fila o el pin del mapa trae lat/lng, se
        // usan tal cual y NUNCA se sobreescriben. Solo se geocodifica cuando
        // faltan (HU-09); un resultado aproximado o vacío deja el local "por revisar".
        Double latitude = req.latitude();
        Double longitude = req.longitude();
        Customer.GeocodeStatus geocodeStatus = Customer.GeocodeStatus.PROVIDED;

        if (latitude == null || longitude == null) {
            var geocoded = geocodingService.geocode(req.address(), req.commune());
            latitude = geocoded.map(GeocodingService.GeoPoint::latitude).orElse(null);
            longitude = geocoded.map(GeocodingService.GeoPoint::longitude).orElse(null);
            geocodeStatus = geocoded.filter(GeocodingService.GeoPoint::precise).isPresent()
                    ? Customer.GeocodeStatus.GEOCODED
                    : Customer.GeocodeStatus.REVIEW;
            if (geocoded.isEmpty()) {
                log.warn("No se pudo geocodificar '{}': el local queda sin coordenadas y por revisar", req.address());
            } else if (geocodeStatus == Customer.GeocodeStatus.REVIEW) {
                log.info("Dirección '{}' geocodificada de forma aproximada: queda por revisar", req.address());
            }
        }

        Customer customer = Customer.builder()
                .fullName(req.fullName())
                .phone(req.phone())
                .address(req.address())
                .commune(req.commune())
                .latitude(latitude)
                .longitude(longitude)
                .geocodeStatus(geocodeStatus)
                .externalCode(req.externalCode())
                .businessType(businessType)
                .build();
        customer.applyDeclaredWindow(declaredWindow);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public ShipmentResponse createShipment(ShipmentRequest req) {
        requireUniqueTrackingCode(req.trackingCode());
        Customer customer = customerRepository.findById(RequestValues.uuid(req.customerId(), "customerId"))
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado: " + req.customerId()));

        Shipment shipment = Shipment.builder()
                .trackingCode(req.trackingCode())
                .customer(customer)
                .scheduledDate(req.scheduledDate())
                .source(req.source())
                .notes(req.notes())
                .stopType(req.stopType() != null ? RequestValues.enumValue(Shipment.StopType.class, req.stopType(), "stopType") : Shipment.StopType.DELIVERY)
                .packageCount(req.packageCount() != null ? req.packageCount() : 1)
                .orderPreference(req.orderPreference() != null
                        ? RequestValues.enumValue(Shipment.OrderPreference.class, req.orderPreference(), "orderPreference")
                        : Shipment.OrderPreference.AUTO)
                .label(req.label())
                .weightKg(req.weightKg() != null ? req.weightKg() : defaultWeightKg)
                .volumeM3(req.volumeM3() != null ? req.volumeM3() : defaultVolumeM3)
                .build();

        return ShipmentResponse.from(shipmentRepository.save(shipment));
    }

    /** Tamaño máximo de página: alcanza para cargar la ruta completa de un día en una sola llamada. */
    public static final int MAX_PAGE_SIZE = 500;

    /** Orden del listado: fecha más reciente primero; dentro del día, por posición en la ruta y tracking. */
    static final Sort LIST_ORDER = Sort.by(
            Sort.Order.desc("scheduledDate"),
            Sort.Order.asc("routePosition").nullsLast(),
            Sort.Order.asc("trackingCode"));

    @Transactional(readOnly = true)
    public PageResponse<ShipmentResponse> search(ShipmentFilter filter, int page, int size) {
        if (page < 0) throw new BadRequestException("page debe ser 0 o mayor");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("size debe estar entre 1 y " + MAX_PAGE_SIZE);
        return PageResponse.from(shipmentRepository
                .findAll(filter.toSpecification(), PageRequest.of(page, size, LIST_ORDER))
                .map(ShipmentResponse::from));
    }

    @Transactional
    public ShipmentResponse updateShipment(String shipmentId, ShipmentUpdateRequest req) {
        Shipment shipment = shipmentRepository.findById(RequestValues.uuid(shipmentId, "shipmentId"))
                .orElseThrow(() -> new EntityNotFoundException("Envío no encontrado"));

        if (req.stopType() != null) shipment.setStopType(RequestValues.enumValue(Shipment.StopType.class, req.stopType(), "stopType"));
        if (req.packageCount() != null) shipment.setPackageCount(req.packageCount());
        if (req.orderPreference() != null) shipment.setOrderPreference(RequestValues.enumValue(Shipment.OrderPreference.class, req.orderPreference(), "orderPreference"));
        if (req.label() != null) shipment.setLabel(req.label());
        if (req.notes() != null) shipment.setNotes(req.notes());
        if (req.weightKg() != null) shipment.setWeightKg(positive(req.weightKg(), "weightKg"));
        if (req.volumeM3() != null) shipment.setVolumeM3(positive(req.volumeM3(), "volumeM3"));

        return ShipmentResponse.from(shipmentRepository.save(shipment));
    }

    @Transactional
    public ShipmentResponse duplicateShipment(String shipmentId, String newTrackingCode) {
        Shipment original = shipmentRepository.findById(RequestValues.uuid(shipmentId, "shipmentId"))
                .orElseThrow(() -> new EntityNotFoundException("Envío no encontrado"));

        requireUniqueTrackingCode(newTrackingCode);

        Shipment copy = Shipment.builder()
                .trackingCode(newTrackingCode)
                .customer(original.getCustomer())
                .scheduledDate(original.getScheduledDate())
                .source(original.getSource())
                .notes(original.getNotes())
                .stopType(original.getStopType())
                .packageCount(original.getPackageCount())
                .orderPreference(original.getOrderPreference())
                .label(original.getLabel())
                .weightKg(original.getWeightKg())
                .volumeM3(original.getVolumeM3())
                .build();

        return ShipmentResponse.from(shipmentRepository.save(copy));
    }

    @Transactional
    public void deleteShipment(String shipmentId) {
        Shipment shipment = shipmentRepository.findById(RequestValues.uuid(shipmentId, "shipmentId"))
                .orElseThrow(() -> new EntityNotFoundException("Envío no encontrado"));
        shipmentRepository.delete(shipment);
    }

    @Transactional(readOnly = true)
    public Shipment findByTrackingCode(String trackingCode) {
        return shipmentRepository.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new EntityNotFoundException("Envío no encontrado: " + trackingCode));
    }

    @Transactional
    public DeliveryAttemptResponse registerAttempt(DeliveryAttemptRequest req) {
        Shipment shipment = shipmentRepository.findById(RequestValues.uuid(req.shipmentId(), "shipmentId"))
                .orElseThrow(() -> new EntityNotFoundException("Envío no encontrado"));
        Customer customer = customerRepository.findById(RequestValues.uuid(req.customerId(), "customerId"))
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));

        DeliveryAttempt.AttemptOutcome outcome = RequestValues.enumValue(DeliveryAttempt.AttemptOutcome.class, req.outcome(), "outcome");

        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .shipment(shipment)
                .customer(customer)
                .attemptedAt(req.attemptedAt() != null ? req.attemptedAt() : Instant.now())
                .outcome(outcome)
                .rejectionReason(req.rejectionReason())
                .build();

        deliveryAttemptRepository.save(attempt);
        forgetProfileAfterCommit(customer.getId());

        shipment.setStatus(outcome == DeliveryAttempt.AttemptOutcome.ACCEPTED
                ? Shipment.ShipmentStatus.DELIVERED
                : Shipment.ShipmentStatus.RESCHEDULED);
        shipmentRepository.save(shipment);

        return DeliveryAttemptResponse.from(attempt);
    }

    private void requireUniqueTrackingCode(String trackingCode) {
        if (trackingCode == null || trackingCode.isBlank()) {
            throw new BadRequestException("trackingCode es obligatorio");
        }
        if (shipmentRepository.existsByTrackingCode(trackingCode)) {
            throw new ConflictException("Ya existe un envío con el trackingCode " + trackingCode);
        }
    }

    private static double positive(double value, String field) {
        if (!(value > 0)) throw new BadRequestException(field + " debe ser mayor que 0");
        return value;
    }

    /**
     * HU-12: el intento cambia la ventana real del local, así que su perfil guardado deja de servir. Se
     * invalida ahora y otra vez al confirmar la transacción, para que una lectura concurrente no vuelva a
     * guardar el perfil sin el intento nuevo.
     */
    private void forgetProfileAfterCommit(UUID customerId) {
        learningService.forget(customerId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            learningService.forget(customerId);
                        }
                    });
        }
    }
}
