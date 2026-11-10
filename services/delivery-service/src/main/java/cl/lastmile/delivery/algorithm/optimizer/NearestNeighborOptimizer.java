package cl.lastmile.delivery.algorithm.optimizer;

import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import org.springframework.stereotype.Component;

/**
 * El nearest-neighbor por franja de la ventana real (HU-04), detrás de {@link RouteOptimizer}.
 * No mira la capacidad ni la jornada: es la línea base de HU-28 y el fallback del VRPTW.
 */
@Component
public class NearestNeighborOptimizer implements RouteOptimizer {

    private final RouteOptimizationService nearestNeighbor;

    public NearestNeighborOptimizer(RouteOptimizationService nearestNeighbor) {
        this.nearestNeighbor = nearestNeighbor;
    }

    @Override
    public OptimizedRoute optimize(RouteProblem problem) {
        return new OptimizedRoute(
                nearestNeighbor.buildRoute(problem.shipments(), problem.depotLat(), problem.depotLng()),
                NEAREST_NEIGHBOR, null);
    }
}
