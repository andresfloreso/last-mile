package cl.lastmile.delivery.algorithm.optimizer;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * La ruta de un camión (HU-28): la entidad que el solver modifica. La variable es el orden de las
 * paradas; el horario se recalcula con {@link #schedule()} con la misma regla que la hora estimada
 * de HU-14 (llega, espera a que abra la ventana real, entrega y sale).
 */
@PlanningEntity
public class TruckRoute {

    private static final int DAY = 24 * 3600;

    @PlanningId
    private String id;
    private TravelTimes travel;
    private int departureSec;
    private int stopSec;
    /** Capacidad y término de jornada; null o NaN si no hay camión registrado (sin límite). */
    private double capacityKg = Double.NaN;
    private double capacityM3 = Double.NaN;
    private Integer shiftEndSec;

    @PlanningListVariable
    private List<Visit> visits = new ArrayList<>();

    /** Para Timefold (clonado de soluciones). */
    public TruckRoute() {}

    public TruckRoute(String id, TravelTimes travel, int departureSec, int stopSec,
                      double capacityKg, double capacityM3, Integer shiftEndSec, List<Visit> visits) {
        this.id = id;
        this.travel = travel;
        this.departureSec = departureSec;
        this.stopSec = stopSec;
        this.capacityKg = capacityKg;
        this.capacityM3 = capacityM3;
        this.shiftEndSec = shiftEndSec;
        this.visits = new ArrayList<>(visits);
    }

    public String getId() {
        return id;
    }

    public List<Visit> getVisits() {
        return visits;
    }

    public void setVisits(List<Visit> visits) {
        this.visits = visits;
    }

    /** Horario y costo de la ruta en el orden actual. */
    public Schedule schedule() {
        int clock = departureSec;
        int at = 0; // depósito
        long meters = 0;
        int late = 0;
        long lateSeconds = 0;
        double kg = 0;
        double m3 = 0;
        for (Visit v : visits) {
            int travelSec = 0;
            if (v.point() >= 0) {
                travelSec = travel.seconds(at, v.point());
                meters += travel.meters(at, v.point());
                at = v.point();
            }
            int arrival = clock + travelSec;
            int serviceStart = arrival < DAY ? Math.max(arrival, v.windowStartSec()) : arrival;
            if (serviceStart >= v.windowEndSec()) {
                late++;
                lateSeconds += serviceStart - v.windowEndSec();
            }
            clock = serviceStart + (v.serviceSec() >= 0 ? v.serviceSec() : stopSec);
            kg += v.weightKg();
            m3 += v.volumeM3();
        }
        return new Schedule(meters, late, lateSeconds, clock, kg, m3,
                excess(kg, capacityKg), excess(m3, capacityM3),
                shiftEndSec == null || visits.isEmpty() ? 0 : Math.max(0, clock - shiftEndSec),
                preferenceViolations());
    }

    private static double excess(double load, double capacity) {
        return Double.isNaN(capacity) ? 0 : Math.max(0, load - capacity);
    }

    /**
     * Paradas "Primera" que no van en el bloque inicial y paradas "Última" que no van en el bloque
     * final (por ejemplo, una Primera detrás de una automática).
     */
    int preferenceViolations() {
        int violations = 0;
        boolean seenNonFirst = false;
        for (Visit v : visits) {
            if (!v.first()) seenNonFirst = true;
            else if (seenNonFirst) violations++;
        }
        boolean seenNonLast = false;
        for (int i = visits.size() - 1; i >= 0; i--) {
            Visit v = visits.get(i);
            if (!v.last()) seenNonLast = true;
            else if (seenNonLast) violations++;
        }
        return violations;
    }

    /**
     * @param endSec             salida de la última parada (segundos desde la medianoche del día)
     * @param excessKg           kg sobre la capacidad del camión (0 si cabe)
     * @param excessM3           m³ sobre la capacidad
     * @param shiftOverrunSec    segundos que la ruta termina después de la jornada
     * @param preferenceViolations paradas Primera/Última fuera de su extremo
     */
    public record Schedule(long meters, int lateStops, long lateSeconds, int endSec, double kg, double m3,
                           double excessKg, double excessM3, int shiftOverrunSec, int preferenceViolations) {

        public boolean feasible() {
            return excessKg <= 0 && excessM3 <= 0 && shiftOverrunSec <= 0;
        }
    }
}
