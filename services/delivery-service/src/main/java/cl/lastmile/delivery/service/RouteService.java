package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.service.StopConsolidator.DeliveryStop;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final ShipmentRepository shipmentRepository;
    private final RouteOptimizationService routeOptimizationService;

    /**
     * Ruta del día por paradas (HU-30).
     *
     * @param stops           paradas en orden; cada una con el envío representante como {@code shipmentId}
     *                        y la suma de bultos de sus envíos
     * @param shipmentsByStop envíos de cada parada, por id del representante (el representante va primero)
     */
    public record ConsolidatedRoute(List<RouteStop> stops, Map<UUID, List<UUID>> shipmentsByStop) {
        public List<UUID> shipmentIds(RouteStop stop) {
            return shipmentsByStop.getOrDefault(stop.shipmentId(), List.of(stop.shipmentId()));
        }
    }

    /**
     * Agrupa los envíos de la fecha en paradas (uno por local), las ordena con
     * {@link RouteOptimizationService} y guarda la posición de la parada en cada uno de sus envíos.
     */
    @Transactional
    public ConsolidatedRoute buildRouteForDate(LocalDate date, double depotLat, double depotLng) {
        List<Shipment> shipments = shipmentRepository.findByScheduledDate(date);
        List<DeliveryStop> stops = StopConsolidator.consolidate(shipments);

        Map<UUID, DeliveryStop> stopByRepresentative = new LinkedHashMap<>();
        stops.forEach(s -> stopByRepresentative.put(s.representative().getId(), s));

        List<RouteStop> ordered = routeOptimizationService.buildRoute(
                stops.stream().map(DeliveryStop::representative).toList(), depotLat, depotLng);

        List<RouteStop> route = new ArrayList<>();
        Map<UUID, List<UUID>> shipmentsByStop = new LinkedHashMap<>();
        for (RouteStop stop : ordered) {
            DeliveryStop group = stopByRepresentative.get(stop.shipmentId());
            for (Shipment shipment : group.shipments()) {
                shipment.setRoutePosition(stop.position());
                shipment.setStatus(Shipment.ShipmentStatus.ROUTED);
            }
            route.add(withPackages(stop, group.packageCount()));
            shipmentsByStop.put(stop.shipmentId(), group.shipmentIds());
        }
        shipmentRepository.saveAll(shipments);

        return new ConsolidatedRoute(route, shipmentsByStop);
    }

    private static RouteStop withPackages(RouteStop s, int packageCount) {
        return new RouteStop(s.shipmentId(), s.customerName(), s.address(), s.position(), s.windowStart(),
                s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(), packageCount,
                s.orderPreference(), s.label(), s.latitude(), s.longitude());
    }
}
