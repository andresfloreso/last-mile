package cl.lastmile.delivery.algorithm.optimizer;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.OptimizedRoute;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.RouteProblem;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HU-28: VRPTW de un camión sobre instancias chicas, sin OSRM (tiempos estimados). */
class TimefoldRouteOptimizerTest {

    private static final double DEPOT_LAT = -33.45;
    private static final double DEPOT_LNG = -70.65;
    /** ~1 km hacia el este a esta latitud. */
    private static final double KM_LNG = 0.01078;

    private final NearestNeighborOptimizer nearestNeighbor = mock(NearestNeighborOptimizer.class);
    private final OsrmRoutingClient osrm = mock(OsrmRoutingClient.class);
    private final TimefoldRouteOptimizer optimizer = new TimefoldRouteOptimizer(
            nearestNeighbor, osrm, 25, Duration.ofSeconds(3), Duration.ofMillis(600));

    private record Local(Shipment shipment, RouteStop stop) {}

    private static Local local(String name, double km, int windowStart, int windowEnd, String preference) {
        Customer c = Customer.builder().id(UUID.randomUUID()).fullName(name).build();
        Shipment s = Shipment.builder().id(UUID.randomUUID()).trackingCode(name).customer(c).build();
        RouteStop stop = new RouteStop(s.getId(), name, "Dirección " + name, 0, LocalTime.of(windowStart, 0),
                LocalTime.of(windowEnd, 0), 1.0, List.of(), "DELIVERY", 1, preference, "Default",
                DEPOT_LAT, DEPOT_LNG + km * KM_LNG);
        return new Local(s, stop);
    }

    /** El nearest-neighbor (mock) devuelve las paradas en el orden dado. */
    private RouteProblem problem(List<Local> baselineOrder, LocalTime departure, int stopMinutes,
                                 RouteOptimizer.Vehicle vehicle, double kgPerStop) {
        List<RouteStop> baseline = new ArrayList<>();
        for (int i = 0; i < baselineOrder.size(); i++) {
            RouteStop s = baselineOrder.get(i).stop();
            baseline.add(new RouteStop(s.shipmentId(), s.customerName(), s.address(), i + 1, s.windowStart(),
                    s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(), s.packageCount(),
                    s.orderPreference(), s.label(), s.latitude(), s.longitude()));
        }
        when(nearestNeighbor.optimize(any())).thenReturn(new OptimizedRoute(baseline, RouteOptimizer.NEAREST_NEIGHBOR, null));
        when(osrm.table(anyList())).thenReturn(Optional.empty());
        return new RouteProblem(
                baselineOrder.stream().map(l -> new RouteOptimizer.Stop(l.shipment(), kgPerStop, 0.05)).toList(),
                DEPOT_LAT, DEPOT_LNG, departure, stopMinutes, vehicle);
    }

    private static List<String> names(OptimizedRoute route) {
        return route.stops().stream().map(RouteStop::customerName).toList();
    }

    private static final RouteOptimizer.Vehicle TRUCK = new RouteOptimizer.Vehicle("KXPT-21", 3500, 18, LocalTime.of(21, 0));

    @Test
    void acortaElRecorridoCuandoTodasLasParadasEstanATiempo() {
        Local a = local("A", 1, 9, 17, "AUTO");
        Local b = local("B", 2, 9, 17, "AUTO");
        Local c = local("C", 3, 9, 17, "AUTO");
        Local d = local("D", 4, 9, 17, "AUTO");
        // Línea base en zigzag: D, A, C, B.
        OptimizedRoute route = optimizer.optimize(problem(List.of(d, a, c, b), LocalTime.of(9, 0), 10, TRUCK, 10));

        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.VRPTW);
        assertThat(route.note()).isNull();
        assertThat(names(route)).containsExactly("A", "B", "C", "D");
        assertThat(route.stops()).extracting(RouteStop::position).containsExactly(1, 2, 3, 4);
    }

    @Test
    void entregaATiempoAntesQueAcortarElRecorrido() {
        // A cierra a las 10:00 y está más lejos; B recibe todo el día y está al lado.
        Local a = local("A", 3, 9, 10, "AUTO");
        Local b = local("B", 1, 9, 17, "AUTO");
        // Línea base B, A: con 60 min por parada, A llega después de las 10:00.
        OptimizedRoute route = optimizer.optimize(problem(List.of(b, a), LocalTime.of(9, 0), 60, TRUCK, 10));

        assertThat(names(route)).containsExactly("A", "B");
    }

    @Test
    void respetaLasParadasPrimeraYUltima() {
        Local first = local("Primera", 4, 9, 17, "FIRST");
        Local a = local("A", 1, 9, 17, "AUTO");
        Local b = local("B", 2, 9, 17, "AUTO");
        Local last = local("Última", 0.5, 9, 17, "LAST");
        OptimizedRoute route = optimizer.optimize(problem(List.of(first, b, a, last), LocalTime.of(9, 0), 10, TRUCK, 10));

        assertThat(names(route)).startsWith("Primera").endsWith("Última");
    }

    @Test
    void laPrimeraSeRespetaAunqueAtraseAOtrasParadas() {
        // La Primera recibe desde las 12:00; las otras cierran a las 11:00. Respetarla las deja fuera de
        // ventana, pero el pedido del despachador pesa más (nivel medio del puntaje).
        Local first = local("Primera", 1, 12, 14, "FIRST");
        Local a = local("A", 2, 9, 11, "AUTO");
        Local b = local("B", 3, 9, 11, "AUTO");
        OptimizedRoute route = optimizer.optimize(problem(List.of(first, a, b), LocalTime.of(9, 0), 10, TRUCK, 10));

        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.VRPTW);
        assertThat(names(route)).containsExactly("Primera", "A", "B");
    }

    @Test
    void laPrimeraPedidaPorElDespachadorMandaAunqueAtraseOtrasParadas() {
        // La Primera recibe al mediodía: ponerla primera atrasa a las de la mañana, y aun así va primera.
        Local first = local("Primera", 1, 12, 14, "FIRST");
        Local m1 = local("M1", 2, 9, 11, "AUTO");
        Local m2 = local("M2", 3, 9, 11, "AUTO");
        OptimizedRoute route = optimizer.optimize(problem(List.of(first, m1, m2), LocalTime.of(8, 0), 10, TRUCK, 10));

        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.VRPTW);
        assertThat(names(route)).containsExactly("Primera", "M1", "M2");
    }

    @Test
    void siLaCargaNoCabeUsaElFallbackYDicePorQue() {
        Local a = local("A", 1, 9, 17, "AUTO");
        Local b = local("B", 2, 9, 17, "AUTO");
        RouteOptimizer.Vehicle small = new RouteOptimizer.Vehicle("FURGON", 30, 18, LocalTime.of(21, 0));
        OptimizedRoute route = optimizer.optimize(problem(List.of(b, a), LocalTime.of(9, 0), 10, small, 20));

        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.NEAREST_NEIGHBOR);
        assertThat(names(route)).containsExactly("B", "A");   // el orden de la línea base
        assertThat(route.note()).contains("FURGON", "la carga (40 kg) supera su capacidad (30 kg)");
    }

    @Test
    void siLaRutaNoTerminaDentroDeLaJornadaUsaElFallback() {
        Local a = local("A", 1, 9, 17, "AUTO");
        RouteOptimizer.Vehicle shortShift = new RouteOptimizer.Vehicle("CORTO", 3500, 18, LocalTime.of(9, 30));
        OptimizedRoute route = optimizer.optimize(problem(List.of(a), LocalTime.of(9, 0), 60, shortShift, 10));

        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.NEAREST_NEIGHBOR);
        assertThat(route.note()).contains("CORTO", "después de su jornada (09:30)");
    }

    @Test
    void siElSolverFallaUsaElFallback() {
        Local a = local("A", 1, 9, 17, "AUTO");
        Local b = local("B", 2, 9, 17, "AUTO");
        RouteProblem problem = problem(List.of(b, a), LocalTime.of(9, 0), 10, TRUCK, 10);
        when(osrm.table(anyList())).thenThrow(new IllegalStateException("tabla rota"));

        OptimizedRoute route = optimizer.optimize(problem);

        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.NEAREST_NEIGHBOR);
        assertThat(names(route)).containsExactly("B", "A");
        assertThat(route.note()).contains("El optimizador falló (IllegalStateException)");
    }

    @Test
    void sinCamionNoHayLimitesYUnaRutaVaciaNoFalla() {
        Local a = local("A", 1, 9, 17, "AUTO");
        Local b = local("B", 2, 9, 17, "AUTO");
        OptimizedRoute route = optimizer.optimize(problem(List.of(b, a), LocalTime.of(9, 0), 10, null, 100_000));
        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.VRPTW);
        assertThat(names(route)).containsExactly("A", "B");

        OptimizedRoute empty = optimizer.optimize(problem(List.of(), LocalTime.of(9, 0), 10, TRUCK, 10));
        assertThat(empty.stops()).isEmpty();
        assertThat(empty.optimizer()).isEqualTo(RouteOptimizer.VRPTW);
    }
}
