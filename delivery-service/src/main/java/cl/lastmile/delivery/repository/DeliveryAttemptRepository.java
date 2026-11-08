package cl.lastmile.delivery.repository;

import cl.lastmile.delivery.model.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    List<DeliveryAttempt> findByCustomerId(UUID customerId);
}
