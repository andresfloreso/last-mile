package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final ShipmentRepository shipmentRepository;
    private final RouteOptimizationService routeOptimizationService;

    @Transactional
    public List<RouteStop> buildRouteForDate(LocalDate date, double depotLat, double depotLng) {
        List<Shipment> shipments = shipmentRepository.findByScheduledDate(date);
        List<RouteStop> route = routeOptimizationService.buildRoute(shipments, depotLat, depotLng);

        Map<java.util.UUID, Integer> positionByShipment = route.stream()
                .collect(java.util.stream.Collectors.toMap(RouteStop::shipmentId, RouteStop::position));

        for (Shipment shipment : shipments) {
            Integer position = positionByShipment.get(shipment.getId());
            if (position != null) {
                shipment.setRoutePosition(position);
                shipment.setStatus(Shipment.ShipmentStatus.ROUTED);
            }
        }
        shipmentRepository.saveAll(shipments);

        return route;
    }
}
