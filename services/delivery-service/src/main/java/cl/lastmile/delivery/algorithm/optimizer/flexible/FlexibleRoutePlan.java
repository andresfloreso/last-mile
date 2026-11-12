package cl.lastmile.delivery.algorithm.optimizer.flexible;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import java.util.List;

/** La solución que resuelve Timefold en el prototipo de HU-37. */
@PlanningSolution
public class FlexibleRoutePlan {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<FlexibleVisit> visits;

    @PlanningEntityCollectionProperty
    private List<FlexibleTruckRoute> trucks;

    @PlanningScore
    private HardMediumSoftLongScore score;

    /** Para Timefold (clonado de soluciones). */
    public FlexibleRoutePlan() {}

    public FlexibleRoutePlan(List<FlexibleVisit> visits, List<FlexibleTruckRoute> trucks) {
        this.visits = visits;
        this.trucks = trucks;
    }

    public List<FlexibleVisit> getVisits() {
        return visits;
    }

    public List<FlexibleTruckRoute> getTrucks() {
        return trucks;
    }

    public HardMediumSoftLongScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftLongScore score) {
        this.score = score;
    }
}
