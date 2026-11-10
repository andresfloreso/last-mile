package cl.lastmile.delivery.algorithm.optimizer;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Restricciones del VRPTW de un camión (HU-28).
 *
 * <p><b>Duras</b> (si no se cumplen, la ruta no es factible y se usa el fallback):
 * <ul>
 *   <li>capacidad del camión en kg y en m³ (HU-29);</li>
 *   <li>la ruta termina dentro de la jornada del camión.</li>
 * </ul>
 * <b>Blandas</b> (la ruta sigue siendo factible si no se cumplen), en dos niveles:
 * <ul>
 *   <li>nivel medio: cada parada Primera/Última fuera de su extremo. Pesa más que todo el nivel de abajo:
 *       el pedido explícito del despachador se respeta aunque cueste paradas fuera de ventana;</li>
 * </ul>
 * y en el nivel blando, en "metros equivalentes", de mayor a menor peso:
 * <ul>
 *   <li>cada parada fuera de su ventana real: {@value #LATE_STOP} (pesa como 1.000 km);</li>
 *   <li>cada minuto fuera de la ventana real: {@value #LATE_MINUTE} (pesa como 10 km);</li>
 *   <li>cada metro recorrido: 1.</li>
 * </ul>
 * Así el solver nunca cambia una parada a tiempo por menos km: primero entrega dentro de la
 * ventana real (lo que mide la primera visita) y después acorta el recorrido.
 */
public class RouteConstraints implements ConstraintProvider {

    static final long LATE_STOP = 1_000_000L;
    static final long LATE_MINUTE = 10_000L;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                factory.forEach(TruckRoute.class)
                        .filter(r -> r.schedule().excessKg() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_HARD, r -> (long) Math.ceil(r.schedule().excessKg() * 1000))
                        .asConstraint("Capacidad en kg"),
                factory.forEach(TruckRoute.class)
                        .filter(r -> r.schedule().excessM3() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_HARD, r -> (long) Math.ceil(r.schedule().excessM3() * 1000))
                        .asConstraint("Capacidad en m3"),
                factory.forEach(TruckRoute.class)
                        .filter(r -> r.schedule().shiftOverrunSec() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_HARD, r -> r.schedule().shiftOverrunSec())
                        .asConstraint("Jornada del camión"),
                factory.forEach(TruckRoute.class)
                        .filter(r -> r.preferenceViolations() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM, r -> (long) r.preferenceViolations())
                        .asConstraint("Primera y Última"),
                factory.forEach(TruckRoute.class)
                        .filter(r -> r.schedule().lateStops() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, r -> {
                            TruckRoute.Schedule s = r.schedule();
                            return LATE_STOP * s.lateStops() + LATE_MINUTE * (s.lateSeconds() / 60);
                        })
                        .asConstraint("Fuera de la ventana real"),
                factory.forEach(TruckRoute.class)
                        .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, r -> r.schedule().meters())
                        .asConstraint("Km recorridos"),
        };
    }
}
