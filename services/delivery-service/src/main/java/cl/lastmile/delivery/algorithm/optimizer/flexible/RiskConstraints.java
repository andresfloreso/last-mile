package cl.lastmile.delivery.algorithm.optimizer.flexible;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

/**
 * Objetivo del prototipo de HU-37. Las duras y la de Primera/Última son las mismas de hoy
 * ({@code RouteConstraints}); lo que cambia es el nivel blando: en vez de castigar cada parada fuera
 * de la ventana real, suma en pesos ({@link RiskCosts}):
 * <ul>
 *   <li>el riesgo de no entrega: probabilidad de que el local no reciba a esa hora × costo de una no entrega;</li>
 *   <li>los minutos de jornada, de la salida de bodega a la vuelta;</li>
 *   <li>los minutos de espera frente a los locales (recargo);</li>
 *   <li>los km, con la vuelta a bodega.</li>
 * </ul>
 */
public class RiskConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                factory.forEach(FlexibleTruckRoute.class)
                        .filter(r -> r.plan().excessKg() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_HARD, r -> (long) Math.ceil(r.plan().excessKg() * 1000))
                        .asConstraint("Capacidad en kg"),
                factory.forEach(FlexibleTruckRoute.class)
                        .filter(r -> r.plan().excessM3() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_HARD, r -> (long) Math.ceil(r.plan().excessM3() * 1000))
                        .asConstraint("Capacidad en m3"),
                factory.forEach(FlexibleTruckRoute.class)
                        .filter(r -> r.plan().shiftOverrunSec() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_HARD, r -> r.plan().shiftOverrunSec())
                        .asConstraint("Jornada del camión"),
                factory.forEach(FlexibleTruckRoute.class)
                        .filter(r -> r.preferenceViolations() > 0)
                        .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM, r -> (long) r.preferenceViolations())
                        .asConstraint("Primera y Última"),
                factory.forEach(FlexibleTruckRoute.class)
                        .penalizeLong(HardMediumSoftLongScore.ONE_SOFT, RiskConstraints::pesos)
                        .asConstraint("Riesgo de no entrega, jornada, espera y km"),
        };
    }

    /** Costo blando de la ruta, en pesos. */
    static long pesos(FlexibleTruckRoute route) {
        FlexibleTruckRoute.Plan p = route.plan();
        RiskCosts c = route.costs();
        double cost = c.failedDelivery() * p.expectedFailures()
                + c.perShiftMinute() * (p.returnSec() - p.departureSec()) / 60.0
                + c.perWaitMinute() * p.waitSec() / 60.0
                + c.perKm() * p.metersWithReturn() / 1000.0;
        return Math.round(cost);
    }
}
