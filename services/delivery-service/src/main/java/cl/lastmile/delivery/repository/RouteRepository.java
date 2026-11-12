package cl.lastmile.delivery.repository;

import cl.lastmile.delivery.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {

    Optional<Route> findByDate(LocalDate date);
}
