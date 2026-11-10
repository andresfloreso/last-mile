package cl.lastmile.delivery.algorithm.optimizer;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.model.Shipment;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Ordena las paradas de la ruta de un camión (HU-28).
 *
 * <p>Implementaciones:
 * <ul>
 *   <li>{@link TimefoldRouteOptimizer}: VRPTW de un camión con Timefold Solver (por defecto);</li>
 *   <li>{@link NearestNeighborOptimizer}: el nearest-neighbor por franja de HU-04, que además es la
 *       línea base y el fallback del anterior.</li>
 * </ul>
 * Se elige con {@code routing.optimizer.engine} ({@code timefold} o {@code nearest-neighbor}).
 */
public interface RouteOptimizer {

    /** Nombre del optimizador que armó la ruta, tal como se informa en la respuesta. */
    String VRPTW = "VRPTW";
    String NEAREST_NEIGHBOR = "NEAREST_NEIGHBOR";

    OptimizedRoute optimize(RouteProblem problem);

    /**
     * Una parada a ordenar: el envío representante (HU-30) y lo que se entrega en ella (HU-29).
     */
    record Stop(Shipment representative, double weightKg, double volumeM3) {
        public Stop {
            Objects.requireNonNull(representative);
        }
    }

    /** Camión que hace la ruta: capacidad y término de su jornada (HU-29). */
    record Vehicle(String plate, double capacityKg, double capacityM3, LocalTime shiftEnd) {}

    /**
     * El problema de un día.
     *
     * @param vehicle null si no hay camiones registrados: la ruta se ordena sin capacidad ni jornada
     */
    record RouteProblem(List<Stop> stops, double depotLat, double depotLng,
                        LocalTime departure, int stopMinutes, Vehicle vehicle) {
        public RouteProblem {
            stops = List.copyOf(stops);
            Objects.requireNonNull(departure);
        }

        public List<Shipment> shipments() {
            return stops.stream().map(Stop::representative).toList();
        }
    }

    /**
     * La ruta ordenada.
     *
     * @param optimizer {@link #VRPTW} o {@link #NEAREST_NEIGHBOR}
     * @param note      por qué se usó el fallback (o por qué la ruta no cumple la capacidad o la
     *                  jornada); null si no hay nada que avisar
     */
    record OptimizedRoute(List<RouteStop> stops, String optimizer, String note) {
        public OptimizedRoute {
            stops = List.copyOf(stops);
        }
    }
}
