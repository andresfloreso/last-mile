package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.OptimizedRoute;
import cl.lastmile.delivery.algorithm.optimizer.UnloadTime;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import cl.lastmile.delivery.service.StopConsolidator.DeliveryStop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Armado manual de la ruta (HU-15): el despachador cambia el orden de la ruta guardada.
 *
 * <ul>
 *   <li>{@link #arrange}: la ruta en el orden pedido, sin escribir nada (para la vista previa). Con
 *       {@code reoptimize}, las paradas fijadas quedan en su posición y el optimizador ordena las demás
 *       en los lugares libres.</li>
 *   <li>{@link #saveOrder}: guarda el orden en la posición de cada envío.</li>
 * </ul>
 * Las paradas se identifican por su envío representante ({@code shipmentId} de la ruta). El orden tiene
 * que traer exactamente las paradas de la ruta guardada: si la ruta cambió mientras se editaba (otro
 * despachador la rearmó, o se importaron envíos), responde 409 para que se vuelva a cargar.
 */
@Service
@RequiredArgsConstructor
public class RouteEditService {

    private final SavedRouteService savedRouteService;
    private final ShipmentRepository shipmentRepository;
    private final RouteService routeService;
    private final RouteOptimizer routeOptimizer;

    /**
     * La ruta guardada en otro orden.
     *
     * @param optimizer quién ordenó las paradas libres al reoptimizar; null si no se reoptimizó
     * @param note      aviso del optimizador (fallback), o null
     */
    public record ArrangedRoute(ConsolidatedRoute route, String optimizer, String note) {}

    @Transactional(readOnly = true)
    public ArrangedRoute arrange(LocalDate date, List<UUID> order, Collection<UUID> pinned, boolean reoptimize,
                                 double depotLat, double depotLng, LocalTime departure, UnloadTime unload,
                                 String vehicleId) {
        ConsolidatedRoute saved = savedRouteService.savedRouteForDate(date);
        Map<UUID, RouteStop> byId = stopsById(saved);
        checkSameStops(byId.keySet(), order);
        Set<UUID> fixed = new HashSet<>(pinned == null ? List.of() : pinned);
        for (UUID id : fixed) {
            if (!byId.containsKey(id)) throw new BadRequestException("La parada fijada " + id + " no está en la ruta");
        }

        List<UUID> arranged = order;
        String optimizer = null;
        String note = null;
        if (reoptimize) {
            List<UUID> free = order.stream().filter(id -> !fixed.contains(id)).toList();
            if (free.size() > 1) {
                OptimizedRoute optimized = optimizeFree(date, free, depotLat, depotLng, departure, unload, vehicleId);
                arranged = mergeIntoFreeSlots(order, fixed, optimized.stops().stream().map(RouteStop::shipmentId).toList());
                optimizer = optimized.optimizer();
                note = optimized.note();
            }
        }

        List<RouteStop> route = new ArrayList<>();
        int position = 1;
        for (UUID id : arranged) route.add(withPosition(byId.get(id), position++));
        return new ArrangedRoute(new ConsolidatedRoute(route, saved.loads(), saved.optimizer(), saved.note(),
                saved.vehicle()), optimizer, note);
    }

    /** Guarda el orden: cada envío de la parada toma la posición de su parada. */
    @Transactional
    public void saveOrder(LocalDate date, List<UUID> order) {
        ConsolidatedRoute saved = savedRouteService.savedRouteForDate(date);
        checkSameStops(stopsById(saved).keySet(), order);
        Map<UUID, Integer> positionByShipment = new HashMap<>();
        int position = 1;
        for (UUID representative : order) {
            for (UUID shipmentId : saved.loads().get(representative).shipmentIds()) {
                positionByShipment.put(shipmentId, position);
            }
            position++;
        }
        List<Shipment> shipments = shipmentRepository.findAllById(positionByShipment.keySet());
        shipments.forEach(s -> s.setRoutePosition(positionByShipment.get(s.getId())));
        shipmentRepository.saveAll(shipments);
    }

    /**
     * Pone las paradas reoptimizadas en los lugares que no están fijados, en el orden del optimizador.
     * Las fijadas quedan en su índice de {@code order}.
     */
    static List<UUID> mergeIntoFreeSlots(List<UUID> order, Set<UUID> fixed, List<UUID> optimizedFree) {
        List<UUID> merged = new ArrayList<>(order.size());
        int next = 0;
        for (UUID id : order) {
            merged.add(fixed.contains(id) ? id : optimizedFree.get(next++));
        }
        return merged;
    }

    /** Si el orden no trae exactamente las paradas de la ruta guardada, la ruta cambió o el pedido está mal. */
    static void checkSameStops(Set<UUID> saved, List<UUID> order) {
        if (order == null || order.isEmpty()) throw new BadRequestException("order no puede estar vacío");
        Set<UUID> requested = new HashSet<>(order);
        if (requested.size() != order.size()) throw new BadRequestException("order repite una parada");
        if (saved.isEmpty()) throw new ConflictException("La fecha no tiene ruta armada");
        if (!requested.equals(saved)) {
            throw new ConflictException("La ruta cambió desde que empezaste a editarla (" + saved.size()
                    + " paradas guardadas, " + order.size() + " en el pedido). Vuelve a cargarla.");
        }
    }

    private OptimizedRoute optimizeFree(LocalDate date, List<UUID> free, double depotLat, double depotLng,
                                        LocalTime departure, UnloadTime unload, String vehicleId) {
        Set<UUID> wanted = new HashSet<>(free);
        List<DeliveryStop> stops = StopConsolidator.consolidate(shipmentRepository.findByScheduledDate(date)).stream()
                .filter(s -> wanted.contains(s.representative().getId()))
                .toList();
        return routeOptimizer.optimize(RouteService.problem(stops, depotLat, depotLng, departure, unload,
                routeService.vehicle(vehicleId)));
    }

    private static Map<UUID, RouteStop> stopsById(ConsolidatedRoute route) {
        Map<UUID, RouteStop> byId = new LinkedHashMap<>();
        route.stops().forEach(s -> byId.put(s.shipmentId(), s));
        return byId;
    }

    private static RouteStop withPosition(RouteStop s, int position) {
        return new RouteStop(s.shipmentId(), s.customerName(), s.address(), position, s.windowStart(),
                s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(), s.packageCount(),
                s.orderPreference(), s.label(), s.latitude(), s.longitude());
    }
}
