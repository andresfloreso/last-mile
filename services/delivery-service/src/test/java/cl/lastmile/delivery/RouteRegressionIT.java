package cl.lastmile.delivery;

import cl.lastmile.delivery.algorithm.optimizer.NearestNeighborOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.OptimizedRoute;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.RouteProblem;
import cl.lastmile.delivery.algorithm.optimizer.TimefoldRouteOptimizer;
import cl.lastmile.delivery.dto.DeliveryDtos.PlannedStopResponse;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.service.RouteTimingService;
import cl.lastmile.delivery.service.StopConsolidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-28: test de regresión del optimizador sobre la semilla (HU-31).
 *
 * <p>Arma la ruta de hoy y la del día cargado con el nearest-neighbor (línea base) y con el VRPTW, y
 * falla si el VRPTW deja más paradas fuera de su ventana real que la línea base, o si tarda más que su
 * límite de tiempo. Sin OSRM (tiempos
 * estimados), así que corre igual en el CI. La tabla con OSRM real está en docs/plan/hu-28/.
 *
 * <p>Solo lee: no guarda el orden (no pasa por RouteService) y el método es @Transactional de solo
 * lectura para cargar los locales.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@Import(TestcontainersConfiguration.class)
class RouteRegressionIT {

    private static final double DEPOT_LAT = -33.446393;
    private static final double DEPOT_LNG = -70.624979;
    private static final LocalTime DEPARTURE = LocalTime.of(8, 0);
    private static final int STOP_MINUTES = 10;
    /** Camión 3/4 de la semilla (HU-29). */
    private static final RouteOptimizer.Vehicle TRUCK =
            new RouteOptimizer.Vehicle("KXPT-21", 3500, 18, LocalTime.of(21, 0));

    @Autowired ShipmentRepository shipments;
    @Autowired NearestNeighborOptimizer nearestNeighbor;
    @Autowired OsrmRoutingClient osrm;
    @Autowired RouteTimingService timing;
    @Autowired JdbcTemplate jdbc;

    /** Lo que mide la tabla antes/después, con la hora estimada de HU-14 (no con el modelo del solver). */
    private record Metrics(int outsideWindow, int lateMinutes, int meters, String optimizer) {}

    private RouteProblem problem(LocalDate date) {
        return new RouteProblem(StopConsolidator.consolidate(shipments.findByScheduledDate(date)).stream()
                .map(s -> new RouteOptimizer.Stop(s.representative(), s.weightKg(), s.volumeM3())).toList(),
                DEPOT_LAT, DEPOT_LNG, DEPARTURE, STOP_MINUTES, TRUCK);
    }

    private Metrics measure(OptimizedRoute route) {
        List<PlannedStopResponse> plan = timing.schedule(route.stops(), DEPOT_LAT, DEPOT_LNG, DEPARTURE, STOP_MINUTES);
        int meters = plan.stream().mapToInt(p -> p.legDistanceMeters() == null ? 0 : p.legDistanceMeters()).sum();
        int lateMinutes = plan.stream().filter(PlannedStopResponse::outsideWindow)
                .mapToInt(p -> p.dayOffset() * 1440 + (p.serviceStart().toSecondOfDay() - p.windowEnd().toSecondOfDay()) / 60)
                .sum();
        return new Metrics((int) plan.stream().filter(PlannedStopResponse::outsideWindow).count(), lateMinutes,
                meters, route.optimizer());
    }

    /**
     * El VRPTW no queda peor que la línea base en el orden de su objetivo: primero paradas fuera de
     * ventana (el criterio de HU-28), después minutos fuera de ventana y después km. Si ninguna parada
     * queda fuera de ventana, los km deben bajar.
     */
    private void compare(LocalDate date, int expectedStops) {
        RouteProblem problem = problem(date);
        assertThat(problem.stops()).hasSize(expectedStops);
        // Límite de tiempo propio, igual al de producción: la comparación no depende de la config de tests.
        TimefoldRouteOptimizer vrptw = new TimefoldRouteOptimizer(nearestNeighbor, osrm, 25,
                Duration.ofSeconds(10), Duration.ofSeconds(3));

        Metrics baseline = measure(nearestNeighbor.optimize(problem));
        long start = System.nanoTime();
        OptimizedRoute route = vrptw.optimize(problem);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        Metrics optimized = measure(route);

        assertThat(elapsed).as("el solver respeta su límite de 10 s (más la tabla y el armado)")
                .isLessThan(Duration.ofSeconds(15));

        assertThat(optimized.optimizer()).as("el VRPTW encuentra una ruta factible").isEqualTo(RouteOptimizer.VRPTW);
        assertThat(optimized.outsideWindow())
                .as("paradas fuera de ventana: VRPTW %s vs nearest-neighbor %s", optimized, baseline)
                .isLessThanOrEqualTo(baseline.outsideWindow());
        if (optimized.outsideWindow() == baseline.outsideWindow()) {
            assertThat(optimized.lateMinutes())
                    .as("minutos fuera de ventana: VRPTW %s vs nearest-neighbor %s", optimized, baseline)
                    .isLessThanOrEqualTo(baseline.lateMinutes() + 1); // ± 1 min: el solver usa la tabla, el horario los tramos
            if (optimized.lateMinutes() >= baseline.lateMinutes()) {
                assertThat(optimized.meters())
                        .as("km: VRPTW %s vs nearest-neighbor %s", optimized, baseline)
                        .isLessThanOrEqualTo(baseline.meters());
            }
        }
        if (baseline.outsideWindow() == 0) {
            assertThat(optimized.meters()).as("sin atrasos, el VRPTW acorta el recorrido").isLessThan(baseline.meters());
        }
    }

    @Test
    @Transactional(readOnly = true)
    void hoyElVrptwNoQuedaPeorQueElNearestNeighbor() {
        compare(jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class), 24);
    }

    @Test
    @Transactional(readOnly = true)
    void enElDiaCargadoElVrptwNoQuedaPeorQueElNearestNeighbor() {
        compare(LocalDate.of(2027, 1, 4), 40);
    }
}
