package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import cl.lastmile.delivery.service.StopConsolidator.DeliveryStop;
import cl.lastmile.delivery.service.StopConsolidator.StopLoad;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La ruta guardada de una fecha (HU-32), tal como la dejó el último {@code POST /api/routes/build}.
 *
 * <p>Solo lee: no llama al optimizador, no cambia la posición ni el estado de ningún envío. Agrupa los
 * envíos en paradas igual que el armado (HU-30) y las devuelve en el orden guardado. La ventana real
 * de cada parada se calcula con los intentos de entrega de hoy, como al armar.
 *
 * <p>Un envío sin posición (por ejemplo, importado después de armar la ruta) no está en la ruta: queda
 * fuera de la respuesta hasta que el despachador vuelva a armarla.
 */
@Service
@RequiredArgsConstructor
public class SavedRouteService {

    private final ShipmentRepository shipmentRepository;
    private final TimeWindowLearningService learningService;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    @Transactional(readOnly = true)
    public ConsolidatedRoute savedRouteForDate(LocalDate date) {
        List<DeliveryStop> stops = StopConsolidator.consolidate(shipmentRepository.findByScheduledDate(date));

        record Positioned(DeliveryStop stop, int position) {}
        List<Positioned> routed = new ArrayList<>();
        for (DeliveryStop stop : stops) {
            stop.shipments().stream()
                    .map(Shipment::getRoutePosition)
                    .filter(Objects::nonNull)
                    .min(Integer::compare)
                    .ifPresent(position -> routed.add(new Positioned(stop, position)));
        }
        routed.sort(Comparator.comparingInt(Positioned::position));

        List<RouteStop> route = new ArrayList<>();
        Map<UUID, StopLoad> loads = new LinkedHashMap<>();
        for (Positioned p : routed) {
            Shipment representative = p.stop().representative();
            route.add(toRouteStop(representative, p.position(), p.stop().packageCount()));
            loads.put(representative.getId(), p.stop().load());
        }
        return new ConsolidatedRoute(route, loads, null, null, null);
    }

    private RouteStop toRouteStop(Shipment s, int position, int packageCount) {
        Customer c = s.getCustomer();
        CustomerTimeWindowProfile profile = learningService.learn(c.getId(), deliveryAttemptRepository);
        return new RouteStop(s.getId(), c.getFullName(), c.getAddress(), position,
                LocalTime.of(profile.recommendedStartHour(), 0), LocalTime.of(profile.recommendedEndHour(), 0),
                profile.confidence(), profile.hoursToAvoid(), s.getStopType().name(), packageCount,
                s.getOrderPreference().name(), s.getLabel(), c.getLatitude(), c.getLongitude());
    }
}
