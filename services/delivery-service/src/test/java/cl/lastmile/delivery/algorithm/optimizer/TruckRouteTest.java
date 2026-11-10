package cl.lastmile.delivery.algorithm.optimizer;

import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HU-28: el horario del solver sigue la misma regla que la hora estimada de HU-14. */
class TruckRouteTest {

    private static final int H = 3600;

    /** Depósito (0) y 2 locales: 10 min y 1 km entre cualquier par de puntos distintos. */
    private static TravelTimes uniform(int points) {
        int[][] s = new int[points][points];
        int[][] m = new int[points][points];
        for (int i = 0; i < points; i++) {
            for (int j = 0; j < points; j++) {
                s[i][j] = i == j ? 0 : 600;
                m[i][j] = i == j ? 0 : 1000;
            }
        }
        return new TravelTimes(s, m, false);
    }

    private static Visit visit(int point, int from, int to, String pref, double kg) {
        return new Visit(UUID.randomUUID(), point, from * H, to * H, pref, kg, 0.1);
    }

    @Test
    void esperaQueAbraLaVentanaYCuentaLasParadasYMinutosFueraDeVentana() {
        Visit a = visit(1, 9, 11, "AUTO", 10);   // llega 08:10, espera hasta 09:00, sale 10:00
        Visit b = visit(2, 9, 10, "AUTO", 20);   // llega 10:10: 10 min fuera de ventana, sale 11:10
        TruckRoute route = new TruckRoute("T", uniform(3), 8 * H, 60 * 60, 100, 1, 12 * H, List.of(a, b));

        TruckRoute.Schedule s = route.schedule();
        assertThat(s.meters()).isEqualTo(2000);
        assertThat(s.lateStops()).isEqualTo(1);
        assertThat(s.lateSeconds()).isEqualTo(600);
        assertThat(s.endSec()).isEqualTo(11 * H + 600);
        assertThat(s.kg()).isEqualTo(30);
        assertThat(s.feasible()).isTrue();
    }

    @Test
    void empezarJustoAlCierreYaEsFueraDeVentana() {
        Visit a = visit(1, 9, 11, "AUTO", 1);
        TruckRoute route = new TruckRoute("T", uniform(2), 10 * H + 50 * 60, 600, Double.NaN, Double.NaN, null, List.of(a));
        TruckRoute.Schedule s = route.schedule();
        assertThat(s.lateStops()).isEqualTo(1);
        assertThat(s.lateSeconds()).isZero();
        assertThat(s.feasible()).isTrue();   // sin camión no hay capacidad ni jornada
    }

    @Test
    void unaParadaSinCoordenadasNoSumaViajeNiMueveElCamion() {
        Visit a = visit(1, 0, 23, "AUTO", 1);
        Visit noLocation = visit(-1, 0, 23, "AUTO", 1);
        Visit b = visit(2, 0, 23, "AUTO", 1);
        TruckRoute route = new TruckRoute("T", uniform(3), 8 * H, 0, Double.NaN, Double.NaN, null, List.of(a, noLocation, b));
        assertThat(route.schedule().meters()).isEqualTo(2000);
        assertThat(route.schedule().endSec()).isEqualTo(8 * H + 1200);
    }

    @Test
    void pasadaLaMedianocheNoEsperaLaVentanaDelDiaAnterior() {
        Visit a = visit(1, 18, 20, "AUTO", 1);
        TruckRoute route = new TruckRoute("T", uniform(2), 24 * H, 0, Double.NaN, Double.NaN, null, List.of(a));
        assertThat(route.schedule().endSec()).isEqualTo(24 * H + 600);
        assertThat(route.schedule().lateStops()).isEqualTo(1);
    }

    @Test
    void detectaExcesoDeCapacidadYDeJornada() {
        Visit a = visit(1, 9, 11, "AUTO", 80);
        Visit b = visit(2, 9, 11, "AUTO", 40);
        TruckRoute route = new TruckRoute("T", uniform(3), 9 * H, 3600, 100, 0.15, 10 * H, List.of(a, b));
        TruckRoute.Schedule s = route.schedule();
        assertThat(s.excessKg()).isEqualTo(20);
        assertThat(s.excessM3()).isCloseTo(0.05, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(s.shiftOverrunSec()).isEqualTo(H + 20 * 60);   // 09:10–10:10, 10:20–11:20
        assertThat(s.feasible()).isFalse();
        assertThat(TimefoldRouteOptimizer.infeasibleNote(
                new RouteOptimizer.Vehicle("X", 100, 0.15, LocalTime.of(10, 0)), s))
                .contains("120 kg", "0.20 m³", "80 min después de su jornada (10:00)");
    }

    @Test
    void cuentaLasPrimerasYUltimasFueraDeSuExtremo() {
        Visit first = visit(1, 9, 11, "FIRST", 1);
        Visit auto = visit(2, 9, 11, "AUTO", 1);
        Visit last = visit(1, 9, 11, "LAST", 1);
        TruckRoute ok = new TruckRoute("T", uniform(3), 0, 0, Double.NaN, Double.NaN, null, List.of(first, auto, last));
        TruckRoute bad = new TruckRoute("T", uniform(3), 0, 0, Double.NaN, Double.NaN, null, List.of(auto, last, first));
        assertThat(ok.preferenceViolations()).isZero();
        assertThat(bad.preferenceViolations()).isEqualTo(2);
        assertThat(new TruckRoute().getVisits()).isEmpty();
        assertThat(new RoutePlan().getScore()).isNull();
    }

    @Test
    void lasDistanciasSalenDeOsrmYLasQueFaltanSeEstiman() {
        OsrmRoutingClient osrm = mock(OsrmRoutingClient.class);
        List<double[]> points = List.of(new double[]{-33.45, -70.65}, new double[]{-33.45, -70.64});
        when(osrm.table(anyList())).thenReturn(Optional.of(new OsrmRoutingClient.Table(
                new double[][]{{0, 120}, {Double.NaN, 0}}, new double[][]{{0, 1500}, {Double.NaN, 0}})));

        TravelTimes t = TravelTimes.of(points, osrm, 25);
        assertThat(t.estimated()).isFalse();
        assertThat(t.seconds(0, 1)).isEqualTo(120);
        assertThat(t.meters(0, 1)).isEqualTo(1500);
        // Sin ruta en OSRM: línea recta (~925 m) × 1,3 a 25 km/h.
        assertThat(t.meters(1, 0)).isBetween(1150, 1250);
        assertThat(t.seconds(1, 0)).isBetween(165, 180);

        when(osrm.table(anyList())).thenReturn(Optional.empty());
        assertThat(TravelTimes.of(points, osrm, 25).estimated()).isTrue();
    }

    @Test
    void elOptimizadorSeEligePorConfiguracion() {
        TimefoldRouteOptimizer timefold = mock(TimefoldRouteOptimizer.class);
        NearestNeighborOptimizer nn = new NearestNeighborOptimizer(mock(RouteOptimizationService.class));
        OptimizerConfig config = new OptimizerConfig();
        assertThat(config.routeOptimizer("timefold", timefold, nn)).isSameAs(timefold);
        assertThat(config.routeOptimizer(" Nearest-Neighbor ", timefold, nn)).isSameAs(nn);
        assertThatThrownBy(() -> config.routeOptimizer("jsprit", timefold, nn))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("jsprit");
    }
}
