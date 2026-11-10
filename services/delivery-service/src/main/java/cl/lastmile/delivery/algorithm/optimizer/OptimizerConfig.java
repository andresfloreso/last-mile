package cl.lastmile.delivery.algorithm.optimizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Elige el optimizador de rutas con {@code routing.optimizer.engine} (HU-28). */
@Configuration
public class OptimizerConfig {

    @Bean
    @Primary
    public RouteOptimizer routeOptimizer(@Value("${routing.optimizer.engine:timefold}") String engine,
                                         TimefoldRouteOptimizer timefold,
                                         NearestNeighborOptimizer nearestNeighbor) {
        return switch (engine.trim().toLowerCase()) {
            case "timefold" -> timefold;
            case "nearest-neighbor" -> nearestNeighbor;
            default -> throw new IllegalStateException(
                    "routing.optimizer.engine debe ser timefold o nearest-neighbor, no: " + engine);
        };
    }
}
