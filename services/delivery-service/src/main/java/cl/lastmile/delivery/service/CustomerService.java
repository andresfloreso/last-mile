package cl.lastmile.delivery.service;

import cl.lastmile.delivery.dto.DeliveryDtos.CustomerResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.DeclaredWindowRequest;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta y edición de locales (HU-25: modulación declarada). */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerResponse get(String customerId) {
        return CustomerResponse.from(find(customerId));
    }

    /** Reemplaza la modulación completa del local; todo en null la elimina. */
    @Transactional
    public CustomerResponse updateDeclaredWindow(String customerId, DeclaredWindowRequest req) {
        Customer customer = find(customerId);
        customer.applyDeclaredWindow(req.toDeclaredWindow().validate());
        return CustomerResponse.from(customer);
    }

    private Customer find(String customerId) {
        return customerRepository.findById(RequestValues.uuid(customerId, "customerId"))
                .orElseThrow(() -> new EntityNotFoundException("Local no encontrado: " + customerId));
    }
}
