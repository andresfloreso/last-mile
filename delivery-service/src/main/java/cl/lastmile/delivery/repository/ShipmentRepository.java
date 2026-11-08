package cl.lastmile.delivery.repository;

import cl.lastmile.delivery.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByTrackingCode(String trackingCode);
    List<Shipment> findByScheduledDate(LocalDate date);
    List<Shipment> findByScheduledDateOrderByRoutePositionAsc(LocalDate date);
}
