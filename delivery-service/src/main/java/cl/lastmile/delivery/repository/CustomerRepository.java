package cl.lastmile.delivery.repository;

import cl.lastmile.delivery.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByExternalCode(String externalCode);
}
