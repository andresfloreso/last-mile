package cl.lastmile.delivery.repository;

import cl.lastmile.delivery.model.Shipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findByTrackingCode(String trackingCode);
    boolean existsByTrackingCode(String trackingCode);
    List<Shipment> findByScheduledDate(LocalDate date);
    List<Shipment> findByScheduledDateOrderByRoutePositionAsc(LocalDate date);

    /** Listado paginado (HU-10). Trae el local en la misma consulta para no hacer una por fila. */
    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Shipment> findAll(Specification<Shipment> spec, Pageable pageable);
}
