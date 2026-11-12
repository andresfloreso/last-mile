package cl.lastmile.delivery.algorithm.optimizer;

import cl.lastmile.delivery.algorithm.optimizer.flexible.FlexibleRouteOptimizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Elige el optimizador de rutas con {@code routing.optimizer.engine} (HU-28) y, para Timefold, el objetivo
 * con {@code routing.optimizer.objective}: {@code windows} (por defecto, la ventana real manda) o
 * {@code risk} (prototipo de HU-37, riesgo de no entrega; apagado hasta validarlo con logística).
 */
@Configuration
public class OptimizerConfig {

    @Bean
    @Primary
    public RouteOptimizer routeOptimizer(@Value("${routing.optimizer.engine:timefold}") String engine,
                                         @Value("${routing.optimizer.objective:windows}") String objective,
                                         TimefoldRouteOptimizer timefold,
                                         FlexibleRouteOptimizer flexible,
                                         NearestNeighborOptimizer nearestNeighbor) {
        boolean risk = switch (objective.trim().toLowerCase()) {
            case "windows" -> false;
            case "risk" -> true;
            default -> throw new IllegalStateException(
                    "routing.optimizer.objective debe ser windows o risk, no: " + objective);
        };
        return switch (engine.trim().toLowerCase()) {
            case "timefold" -> risk ? flexible : timefold;
            case "nearest-neighbor" -> nearestNeighbor;
            default -> throw new IllegalStateException(
                    "routing.optimizer.engine debe ser timefold o nearest-neighbor, no: " + engine);
        };
    }
}
