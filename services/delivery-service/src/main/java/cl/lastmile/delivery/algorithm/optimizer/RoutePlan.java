package cl.lastmile.delivery.algorithm.optimizer;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import java.util.List;

/** La solución que resuelve Timefold (HU-28): las paradas del día y la ruta del camión. */
@PlanningSolution
public class RoutePlan {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Visit> visits;

    /** Un camión por ahora; HU-16 agrega más. */
    @PlanningEntityCollectionProperty
    private List<TruckRoute> trucks;

    @PlanningScore
    private HardMediumSoftLongScore score;

    /** Para Timefold (clonado de soluciones). */
    public RoutePlan() {}

    public RoutePlan(List<Visit> visits, List<TruckRoute> trucks) {
        this.visits = visits;
        this.trucks = trucks;
    }

    public List<Visit> getVisits() {
        return visits;
    }

    public List<TruckRoute> getTrucks() {
        return trucks;
    }

    public HardMediumSoftLongScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftLongScore score) {
        this.score = score;
    }
}
