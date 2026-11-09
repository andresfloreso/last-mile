package cl.lastmile.delivery.service;

import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import cl.lastmile.delivery.dto.DeliveryDtos.*;
import cl.lastmile.delivery.geo.GeocodingService;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeclaredWindow;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.CustomerRepository;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.repository.ShipmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .build();

        return ShipmentResponse.from(shipmentRepository.save(shipment));
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> findByDate(java.time.LocalDate date) {
        return shipmentRepository.findByScheduledDate(date).stream().map(ShipmentResponse::from).toList();
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
}
