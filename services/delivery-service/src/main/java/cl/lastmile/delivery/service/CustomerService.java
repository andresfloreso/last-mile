package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.dto.DeliveryDtos.CustomerResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.TimeWindowResponse;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.dto.DeliveryDtos.DeclaredWindowRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.LocationRequest;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta y edición de locales (HU-25: modulación declarada; HU-09: ubicación). */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final TimeWindowLearningService learningService;

    @Transactional(readOnly = true)
    public CustomerResponse get(String customerId) {
        return CustomerResponse.from(find(customerId));
    }

    /**
     * Modulación vs. ventana real (HU-11). La ventana sale de {@link TimeWindowLearningService#learn},
     * el mismo cálculo que usa la ruta, así que la pantalla y la ruta no pueden diferir.
     */
    @Transactional(readOnly = true)
    public TimeWindowResponse timeWindow(String customerId) {
        Customer customer = find(customerId);
        CustomerTimeWindowProfile profile = learningService.learn(customer.getId(), deliveryAttemptRepository);
        return TimeWindowResponse.from(customer, profile, TimeWindowSummary.of(profile));
    }

    /** Reemplaza la modulación completa del local; todo en null la elimina. */
    @Transactional
    public CustomerResponse updateDeclaredWindow(String customerId, DeclaredWindowRequest req) {
        Customer customer = find(customerId);
        customer.applyDeclaredWindow(req.toDeclaredWindow().validate());
        return CustomerResponse.from(customer);
    }

    /** Fija la ubicación a mano; el local deja de estar "por revisar". */
    @Transactional
    public CustomerResponse updateLocation(String customerId, LocationRequest req) {
        if (req.latitude() < -90 || req.latitude() > 90) {
            throw new BadRequestException("latitude debe estar entre -90 y 90");
        }
        if (req.longitude() < -180 || req.longitude() > 180) {
            throw new BadRequestException("longitude debe estar entre -180 y 180");
        }
        Customer customer = find(customerId);
        customer.relocate(req.latitude(), req.longitude());
        return CustomerResponse.from(customer);
    }

    private Customer find(String customerId) {
        return customerRepository.findById(RequestValues.uuid(customerId, "customerId"))
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado: " + customerId));
    }
}
