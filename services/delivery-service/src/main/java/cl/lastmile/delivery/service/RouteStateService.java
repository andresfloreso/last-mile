package cl.lastmile.delivery.service;

import cl.lastmile.delivery.dto.DeliveryDtos.RouteStateResponse;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import cl.lastmile.delivery.model.Route;
import cl.lastmile.delivery.model.Route.Status;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Vehicle;
import cl.lastmile.delivery.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de la ruta del día (HU-17): borrador → confirmada (con conductor) → en curso → cerrada.
 *
 * <p>Solo un borrador se rearma ({@code POST /api/routes/build}) o se reordena a mano
 * ({@code PUT /api/routes/order}, HU-15). Una ruta confirmada vuelve a borrador si el despachador
 * necesita cambiarla antes de que salga el camión.
 */
@Service
@RequiredArgsConstructor
public class RouteStateService {

    /** Cambios de estado permitidos. */
    static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.DRAFT, EnumSet.of(Status.CONFIRMED),
            Status.CONFIRMED, EnumSet.of(Status.DRAFT, Status.IN_PROGRESS),
            Status.IN_PROGRESS, EnumSet.of(Status.CLOSED),
            Status.CLOSED, EnumSet.noneOf(Status.class));

    private final RouteRepository routeRepository;

    /** Estado de la ruta de la fecha; vacío si nunca se armó. */
    @Transactional(readOnly = true)
    public Optional<RouteStateResponse> state(LocalDate date) {
        return routeRepository.findByDate(date).map(RouteStateResponse::from);
    }

    /** 409 si la ruta de la fecha ya no es un borrador. */
    @Transactional(readOnly = true)
    public void checkEditable(LocalDate date) {
        routeRepository.findByDate(date).ifPresent(RouteStateService::checkEditable);
    }

    static void checkEditable(Route route) {
        if (!route.isEditable()) {
            throw new ConflictException("La ruta del " + route.getDate() + " está " + label(route.getStatus())
                    + ": ya no se reordena. Vuélvela a borrador para cambiarla.");
        }
    }

    /**
     * Guarda lo que dejó el armado: la ruta queda (o sigue) en borrador, con el optimizador y el camión
     * que la ordenaron, y cada envío apunta a ella. Corre en la transacción del armado.
     */
    @Transactional
    public Route recordBuild(LocalDate date, String optimizer, String note, Vehicle vehicle, Collection<Shipment> shipments) {
        Route route = routeRepository.findByDate(date).orElseGet(() -> Route.builder().date(date).build());
        checkEditable(route);
        route.setOptimizer(optimizer);
        route.setOptimizerNote(note);
        route.setVehicle(vehicle);
        route.setUpdatedAt(Instant.now());
        Route saved = routeRepository.save(route);
        shipments.forEach(s -> s.setRouteId(saved.getId()));
        return saved;
    }

    /**
     * Cambia el estado. Confirmar exige un conductor; volver a confirmar una ruta confirmada cambia el
     * conductor. Volver a borrador lo libera.
     *
     * @param driverId   id del usuario conductor (auth-service); obligatorio al confirmar
     * @param driverName su nombre, para mostrarlo
     */
    @Transactional
    public RouteStateResponse changeStatus(LocalDate date, Status target, UUID driverId, String driverName) {
        Route route = routeRepository.findByDate(date)
                .orElseThrow(() -> new EntityNotFoundException("La fecha " + date + " no tiene ruta armada"));
        Status from = route.getStatus();
        if (from == target && target != Status.CONFIRMED) return RouteStateResponse.from(route);
        if (from != target && !TRANSITIONS.get(from).contains(target)) {
            throw new ConflictException("Una ruta " + label(from) + " no puede pasar a " + label(target));
        }
        Instant now = Instant.now();
        if (target == Status.CONFIRMED) {
            if (driverId == null) throw new BadRequestException("Para confirmar la ruta hay que asignar un conductor (driverId)");
            route.setDriverId(driverId);
            route.setDriverName(driverName == null || driverName.isBlank() ? null : driverName.strip());
            route.setConfirmedAt(now);
        } else if (target == Status.DRAFT) {
            route.setDriverId(null);
            route.setDriverName(null);
            route.setConfirmedAt(null);
        }
        route.setStatus(target);
        route.setUpdatedAt(now);
        return RouteStateResponse.from(routeRepository.save(route));
    }

    static String label(Status status) {
        return switch (status) {
            case DRAFT -> "en borrador";
            case CONFIRMED -> "confirmada";
            case IN_PROGRESS -> "en curso";
            case CLOSED -> "cerrada";
        };
    }
}
