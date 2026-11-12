package cl.lastmile.delivery.algorithm.optimizer.flexible;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.HourBucket;
import cl.lastmile.delivery.algorithm.optimizer.TravelTimes;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.model.DeclaredWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Prototipo de HU-37: curva de probabilidad y regla flexible del horario. */
class FlexibleTruckRouteTest {

    private static final int H = 3600;

    /** Curva con p = base todo el día, salvo las horas indicadas. */
    private static AcceptanceCurve curve(double base, int[] hours, double[] values) {
        double[] p = new double[24];
        java.util.Arrays.fill(p, base);
        for (int i = 0; i < hours.length; i++) p[hours[i]] = values[i];
        return AcceptanceCurve.ofHours(p);
    }

    /** Tiempos por línea recta (OSRM no responde) entre la bodega y puntos sobre una misma calle. */
    private static TravelTimes travel(int points) {
        OsrmRoutingClient osrm = mock(OsrmRoutingClient.class);
        when(osrm.table(anyList())).thenReturn(Optional.empty());
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < points; i++) pts.add(new double[]{-33.44, -70.62 + i * 0.01});
        return TravelTimes.of(pts, osrm, 25);
    }

    private static FlexibleVisit visit(int point, AcceptanceCurve curve, int windowStartHour) {
        return new FlexibleVisit(UUID.randomUUID(), point, curve, windowStartHour * H, (windowStartHour + 2) * H, "AUTO", 10, 0.05);
    }

    private static FlexibleTruckRoute route(TravelTimes travel, FlexibleVisit... visits) {
        return new FlexibleTruckRoute("T", travel, 8 * H, 10 * 60, Double.NaN, Double.NaN, 21 * H, RiskCosts.DEFAULT, List.of(visits));
    }

    @Test
    void laCurvaMezclaElHistorialConLaModulacion() {
        List<HourBucket> histogram = new ArrayList<>(IntStream.range(0, 24).mapToObj(h -> new HourBucket(h, 0, 0)).toList());
        histogram.set(9, new HourBucket(9, 13, 1));   // 13 de 14 en la mañana
        histogram.set(15, new HourBucket(15, 0, 2));  // cerrado en la tarde
        DeclaredWindow declared = new DeclaredWindow(LocalTime.of(9, 0), LocalTime.of(12, 0), null, null);

        AcceptanceCurve c = AcceptanceCurve.of(histogram, declared);

        assertThat(c.atHour(9)).isCloseTo((13 + 2 * 0.6) / 16, within(1e-9));  // manda el historial
        assertThat(c.atHour(11)).isCloseTo(0.6, within(1e-9));                // sin visitas: la modulación
        assertThat(c.atHour(15)).isCloseTo((0 + 2 * 0.2) / 4, within(1e-9));   // rechazos y fuera de la modulación
        assertThat(c.atHour(20)).isCloseTo(0.2, within(1e-9));
        assertThat(AcceptanceCurve.of(histogram, DeclaredWindow.NONE).atHour(20)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void unaModulacionDesmentidaPorElHistorialDejaDeContar() {
        // Declara 09–12, pero a las 11 lo encontraron cerrado dos veces; recibe a las 19.
        List<HourBucket> histogram = new ArrayList<>(IntStream.range(0, 24).mapToObj(h -> new HourBucket(h, 0, 0)).toList());
        histogram.set(11, new HourBucket(11, 0, 2));
        histogram.set(19, new HourBucket(19, 13, 1));
        DeclaredWindow declared = new DeclaredWindow(LocalTime.of(9, 0), LocalTime.of(12, 0), null, null);

        AcceptanceCurve c = AcceptanceCurve.of(histogram, declared);

        assertThat(c.atHour(9)).isCloseTo(0.2, within(1e-9));
        assertThat(c.atHour(19)).isCloseTo((13 + 2 * 0.2) / 16, within(1e-9));
        // Sin visitas dentro de la modulación, todavía cuenta.
        histogram.set(11, new HourBucket(11, 0, 0));
        assertThat(AcceptanceCurve.of(histogram, declared).atHour(9)).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void esperaSoloSiLaProbabilidadSubeLoSuficiente() {
        FlexibleTruckRoute r = route(travel(2));
        // Llega 10:30. Esperar 30 min cuesta 30 × 300 = 9.000; subir de 0,6 a 0,9 ahorra 0,3 × 60.000 = 18.000.
        assertThat(r.bestStart(10 * H + 1800, curve(0.6, new int[]{11}, new double[]{0.9}))).isEqualTo(11 * H);
        // Subir de 0,6 a 0,7 ahorra 6.000: no conviene esperar.
        assertThat(r.bestStart(10 * H + 1800, curve(0.6, new int[]{11}, new double[]{0.7}))).isEqualTo(10 * H + 1800);
        // Nunca espera más de 120 min.
        assertThat(r.bestStart(8 * H + 60, curve(0.1, new int[]{11}, new double[]{1.0}))).isLessThan(11 * H);
    }

    @Test
    void enVezDeEsperarEnLaPrimeraParadaSaleMasTardeDeBodega() {
        AcceptanceCurve morning = curve(0.2, new int[]{9, 10}, new double[]{0.9, 0.9});
        FlexibleTruckRoute r = route(travel(2), visit(1, morning, 9));

        FlexibleTruckRoute.Plan flexible = r.plan();
        assertThat(flexible.stops().get(0).serviceStartSec()).isEqualTo(9 * H);
        assertThat(flexible.waitSec()).isZero();
        assertThat(flexible.departureSec()).isGreaterThan(8 * H);

        FlexibleTruckRoute.Plan today = r.plan(FlexibleTruckRoute.Mode.RIGID);
        assertThat(today.departureSec()).isEqualTo(8 * H);
        assertThat(today.stops().get(0).serviceStartSec()).isEqualTo(9 * H);
        assertThat(today.waitSec()).isGreaterThan(50 * 60);
        assertThat(today.shiftMinutes()).isGreaterThan(flexible.shiftMinutes());
    }

    @Test
    void laReglaDeHoyEsperaAQueAbraLaVentanaAunqueElLocalPuedaRecibirAntes() {
        // Recibe bien desde las 10 (0,8), la ventana aprendida abre a las 11.
        AcceptanceCurve c = curve(0.8, new int[]{11, 12}, new double[]{0.9, 0.9});
        FlexibleTruckRoute r = new FlexibleTruckRoute("T", travel(3), 10 * H, 10 * 60, Double.NaN, Double.NaN, null,
                RiskCosts.DEFAULT, List.of(visit(1, curve(0.9, new int[]{}, new double[]{}), 10), visit(2, c, 11)));

        FlexibleTruckRoute.Plan today = r.plan(FlexibleTruckRoute.Mode.RIGID);
        FlexibleTruckRoute.Plan flexible = r.plan();
        assertThat(today.stops().get(1).serviceStartSec()).isEqualTo(11 * H);
        assertThat(flexible.stops().get(1).serviceStartSec()).isLessThan(11 * H);
        assertThat(flexible.expectedFailures()).isCloseTo(0.1 + 0.2, within(1e-9));
        assertThat(flexible.expectedFirstVisitRate()).isCloseTo(0.85, within(1e-9));
    }

    @Test
    void elCostoSumaRiesgoJornadaEsperaYKm() {
        AcceptanceCurve c = curve(0.5, new int[]{}, new double[]{});
        FlexibleTruckRoute r = route(travel(2), visit(1, c, 8));
        FlexibleTruckRoute.Plan p = r.plan();
        long expected = Math.round(60_000 * 0.5
                + 250 * (p.returnSec() - p.departureSec()) / 60.0
                + 50 * p.waitSec() / 60.0
                + 700 * p.metersWithReturn() / 1000.0);
        assertThat(RiskConstraints.pesos(r)).isEqualTo(expected);
        assertThat(p.metersWithReturn()).isEqualTo(2 * p.meters());
    }
}
