package cl.lastmile.delivery;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.RouteProblem;
import cl.lastmile.delivery.algorithm.optimizer.flexible.FlexibleRouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.flexible.FlexibleRouteOptimizer.Comparison;
import cl.lastmile.delivery.algorithm.optimizer.flexible.FlexibleTruckRoute;
import cl.lastmile.delivery.model.Vehicle;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.repository.VehicleRepository;
import cl.lastmile.delivery.service.StopConsolidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Análisis de ventanas flexibles (base de HU-37, docs/plan/analisis-ventanas-flexibles.md).
 *
 * <p>Arma la ruta de los dos días de la semilla (hoy y el día cargado) con el optimizador de hoy y con el
 * prototipo, y mide las dos con la misma vara: km, salida y vuelta, minutos de espera y probabilidad
 * esperada de entregar en primera visita. Escribe {@code target/analisis-ventanas-flexibles/*.json}.
 *
 * <p>No corre en CI: necesita {@code -Danalisis=true} y, para tiempos por calle, OSRM
 * ({@code -Dosrm=https://router.project-osrm.org}). Sin OSRM usa línea recta × 1,3 a 25 km/h.
 * <pre>
 * ./mvnw -o verify -Dit.test=FlexibleWindowsAnalysisIT -Dsurefire.skip=true -Danalisis=true -Dosrm=https://router.project-osrm.org
 * </pre>
 */
@EnabledIfSystemProperty(named = "analisis", matches = "true")
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=${osrm:http://localhost:1}",
        "geocoding.nominatim-url=http://localhost:1"
})
@Import(TestcontainersConfiguration.class)
class FlexibleWindowsAnalysisIT {

    private static final double DEPOT_LAT = -33.446393;
    private static final double DEPOT_LNG = -70.624979;
    private static final LocalDate LOADED_DAY = LocalDate.of(2027, 1, 4);

    @Autowired FlexibleRouteOptimizer optimizer;
    @Autowired ShipmentRepository shipments;
    @Autowired VehicleRepository vehicles;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void midelaRutaDeHoyYLaDelPrototipoEnLosDosDiasDeLaSemilla() throws Exception {
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        Path out = Path.of("target", "analisis-ventanas-flexibles");
        Files.createDirectories(out);
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> all = new LinkedHashMap<>();
        for (Map.Entry<String, LocalDate> day : Map.of("hoy", today, "dia-cargado", LOADED_DAY).entrySet()) {
            Comparison c = optimizer.compare(problem(day.getValue()));
            assertThat(c.flexible().stops()).hasSameSizeAs(c.today().stops());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fecha", day.getValue().toString());
            result.put("hoy", describe(c.today().stops(), c.todayPlan()));
            result.put("prototipo", describe(c.flexible().stops(), c.flexiblePlan()));
            all.put(day.getKey(), result);
        }
        Files.writeString(out.resolve("resultados.json"), json.writeValueAsString(all));
    }

    private RouteProblem problem(LocalDate date) {
        Vehicle vehicle = vehicles.findByActiveTrueOrderByPlateAsc().stream()
                .max(Comparator.comparingDouble(Vehicle::getCapacityKg)).orElseThrow();
        List<StopConsolidator.DeliveryStop> stops = StopConsolidator.consolidate(shipments.findByScheduledDate(date));
        return new RouteProblem(
                stops.stream().map(s -> new RouteOptimizer.Stop(s.representative(), s.weightKg(), s.volumeM3())).toList(),
                DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10,
                new RouteOptimizer.Vehicle(vehicle.getPlate(), vehicle.getCapacityKg(), vehicle.getCapacityM3(), vehicle.getShiftEnd()));
    }

    private static Map<String, Object> describe(List<RouteStop> route, FlexibleTruckRoute.Plan plan) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("paradas", route.size());
        r.put("km", plan.meters() / 1000.0);
        r.put("kmConVuelta", plan.metersWithReturn() / 1000.0);
        r.put("salida", hhmm(plan.departureSec()));
        r.put("ultimaEntrega", hhmm(plan.endSec()));
        r.put("vuelta", hhmm(plan.returnSec()));
        r.put("jornadaMin", plan.shiftMinutes());
        r.put("esperaMin", plan.waitSec() / 60);
        r.put("primeraVisitaEsperada", plan.expectedFirstVisitRate());
        r.put("entregasFallidasEsperadas", plan.expectedFailures());
        r.put("fueraDeVentana", plan.lateStops());
        List<Map<String, Object>> stops = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) {
            RouteStop s = route.get(i);
            FlexibleTruckRoute.StopPlan p = plan.stops().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pos", i + 1);
            m.put("local", s.customerName());
            m.put("lat", s.latitude());
            m.put("lng", s.longitude());
            m.put("ventana", s.windowStart() + "–" + s.windowEnd());
            m.put("llega", hhmm(p.arrivalSec()));
            m.put("descarga", hhmm(p.serviceStartSec()));
            m.put("esperaMin", p.waitSec() / 60);
            m.put("probabilidad", Math.round(p.probability() * 100) / 100.0);
            stops.add(m);
        }
        r.put("detalle", stops);
        return r;
    }

    private static String hhmm(int sec) {
        return String.format("%02d:%02d", sec / 3600, (sec % 3600) / 60);
    }
}
