package cl.lastmile.delivery.algorithm.optimizer.flexible;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.HourBucket;
import cl.lastmile.delivery.model.DeclaredWindow;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Probabilidad de que un local reciba, hora por hora (prototipo de HU-37, ver
 * docs/plan/analisis-ventanas-flexibles.md).
 *
 * <p>Para cada hora del día:
 * <pre>
 *   p(h) = (aceptadas en h + PESO_PREVIO × previo(h)) / (visitas en h + PESO_PREVIO)
 * </pre>
 * donde {@code previo(h)} es {@value #PRIOR_IN_DECLARED} si la hora está dentro de la modulación
 * del local y {@value #PRIOR_OUTSIDE} si no. Con muchas visitas en una hora manda el historial; con
 * pocas o ninguna, lo que el local declara. Sin modulación ni historial, la probabilidad es
 * {@value #PRIOR_UNKNOWN} todo el día.
 *
 * <p><b>Modulación desmentida:</b> si el camión ya fue al local dentro de su modulación y nunca le
 * recibieron, la modulación deja de contar y el previo es {@value #PRIOR_OUTSIDE} todo el día. Así un
 * local que declara 09–12 pero recibe después de las 19 no atrae visitas a las 9.
 */
public final class AcceptanceCurve {

    /** Cuántas visitas "vale" la modulación frente al historial. */
    static final double PRIOR_WEIGHT = 2.0;
    static final double PRIOR_IN_DECLARED = 0.6;
    static final double PRIOR_OUTSIDE = 0.2;
    static final double PRIOR_UNKNOWN = 0.5;

    private final double[] byHour;

    private AcceptanceCurve(double[] byHour) {
        this.byHour = byHour;
    }

    /**
     * @param histogram 24 casilleros de {@code TimeWindowLearningService} (aceptadas y no aceptadas por hora)
     * @param declared  modulación del local; null o vacía si no declaró
     */
    public static AcceptanceCurve of(List<HourBucket> histogram, DeclaredWindow declared) {
        DeclaredWindow trusted = disproved(histogram, declared) ? null : declared;
        double[] p = new double[24];
        for (int h = 0; h < 24; h++) {
            double prior = trusted == null && declared != null && !declared.isEmpty() ? PRIOR_OUTSIDE : prior(trusted, h);
            HourBucket b = histogram != null && h < histogram.size() ? histogram.get(h) : null;
            int accepted = b == null ? 0 : b.accepted();
            int attempts = b == null ? 0 : b.attempts();
            p[h] = (accepted + PRIOR_WEIGHT * prior) / (attempts + PRIOR_WEIGHT);
        }
        return new AcceptanceCurve(p);
    }

    /** true si hubo visitas dentro de la modulación y ninguna fue aceptada. */
    static boolean disproved(List<HourBucket> histogram, DeclaredWindow declared) {
        if (histogram == null || declared == null || declared.isEmpty()) return false;
        int attempts = 0;
        int accepted = 0;
        for (HourBucket b : histogram) {
            if (b.hour() >= 0 && b.hour() < 24 && declared.contains(LocalTime.of(b.hour(), 30))) {
                attempts += b.attempts();
                accepted += b.accepted();
            }
        }
        return attempts > 0 && accepted == 0;
    }

    /** Curva fija, para pruebas. */
    public static AcceptanceCurve ofHours(double[] byHour) {
        if (byHour.length != 24) throw new IllegalArgumentException("Se esperan 24 horas");
        return new AcceptanceCurve(byHour.clone());
    }

    /** Previo de la hora: si la mitad de la hora cae dentro de la modulación. */
    static double prior(DeclaredWindow declared, int hour) {
        if (declared == null || declared.isEmpty()) return PRIOR_UNKNOWN;
        return declared.contains(LocalTime.of(hour, 30)) ? PRIOR_IN_DECLARED : PRIOR_OUTSIDE;
    }

    /** Probabilidad de que reciba si el camión empieza a descargar a esta hora (segundos desde la medianoche). */
    public double at(int secondOfDay) {
        int hour = Math.floorMod(secondOfDay / 3600, 24);
        return byHour[hour];
    }

    public double atHour(int hour) {
        return byHour[hour];
    }

    @Override
    public String toString() {
        return Arrays.toString(byHour);
    }
}
