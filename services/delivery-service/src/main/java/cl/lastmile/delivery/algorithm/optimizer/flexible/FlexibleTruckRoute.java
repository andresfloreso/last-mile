package cl.lastmile.delivery.algorithm.optimizer.flexible;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import cl.lastmile.delivery.algorithm.optimizer.TravelTimes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La ruta de un camión en el prototipo de HU-37 (ver docs/plan/analisis-ventanas-flexibles.md).
 *
 * <p>Se diferencia de la ruta de hoy ({@code TruckRoute}) en dos reglas del horario:
 * <ul>
 *   <li><b>No espera siempre a que abra la ventana.</b> Al llegar a un local, el camión elige entre
 *       descargar ya o esperar hasta el comienzo de una hora siguiente (a lo más
 *       {@link RiskCosts#maxWaitMinutes()}), lo que cueste menos: el riesgo de no entrega a esa hora
 *       contra los minutos de espera.</li>
 *   <li><b>Elige la hora de salida.</b> Si en la primera parada iba a esperar, sale más tarde de bodega
 *       (hasta {@link RiskCosts#maxDepartureDelayMinutes()}) y llega justo.</li>
 * </ul>
 * {@link #plan(Mode)} con {@link Mode#RIGID} calcula el horario con la regla de hoy (sale a la hora fija
 * y espera a que abra la ventana real), para medir las dos rutas con la misma vara.
 */
@PlanningEntity
public class FlexibleTruckRoute {

    private static final int DAY = 24 * 3600;

    /** Cómo se arma el horario. */
    public enum Mode { FLEXIBLE, RIGID }

    @PlanningId
    private String id;
    private TravelTimes travel;
    private int departureSec;
    private int stopSec;
    private double capacityKg = Double.NaN;
    private double capacityM3 = Double.NaN;
    private Integer shiftEndSec;
    private RiskCosts costs = RiskCosts.DEFAULT;

    @PlanningListVariable
    private List<FlexibleVisit> visits = new ArrayList<>();

    /** Para Timefold (clonado de soluciones). */
    public FlexibleTruckRoute() {}

    public FlexibleTruckRoute(String id, TravelTimes travel, int departureSec, int stopSec, double capacityKg,
                              double capacityM3, Integer shiftEndSec, RiskCosts costs, List<FlexibleVisit> visits) {
        this.id = id;
        this.travel = travel;
        this.departureSec = departureSec;
        this.stopSec = stopSec;
        this.capacityKg = capacityKg;
        this.capacityM3 = capacityM3;
        this.shiftEndSec = shiftEndSec;
        this.costs = costs;
        this.visits = new ArrayList<>(visits);
    }

    public String getId() {
        return id;
    }

    public List<FlexibleVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<FlexibleVisit> visits) {
        this.visits = visits;
    }

    RiskCosts costs() {
        return costs;
    }

    /** Horario con la regla flexible (la que optimiza el prototipo). */
    public Plan plan() {
        return plan(Mode.FLEXIBLE);
    }

    public Plan plan(Mode mode) {
        List<StopPlan> stops = new ArrayList<>(visits.size());
        int clock = departureSec;
        int departure = departureSec;
        int at = 0;
        long meters = 0;
        long waitSec = 0;
        double expectedFailures = 0;
        double kg = 0;
        double m3 = 0;
        int lateStops = 0;
        for (FlexibleVisit v : visits) {
            int travelSec = 0;
            if (v.point() >= 0) {
                travelSec = travel.seconds(at, v.point());
                meters += travel.meters(at, v.point());
                at = v.point();
            }
            int arrival = clock + travelSec;
            int service = mode == Mode.RIGID ? rigidStart(arrival, v) : bestStart(arrival, v.curve());
            if (stops.isEmpty() && mode == Mode.FLEXIBLE && service > arrival) {
                // Sale más tarde de bodega en vez de esperar frente a la primera parada.
                int delay = Math.min(service - arrival, costs.maxDepartureDelayMinutes() * 60);
                departure += delay;
                arrival += delay;
            }
            if (service >= v.windowEndSec()) lateStops++;
            double p = v.curve().at(service);
            expectedFailures += 1 - p;
            waitSec += service - arrival;
            stops.add(new StopPlan(v.id(), arrival, service, service - arrival, p));
            clock = service + stopSec;
            kg += v.weightKg();
            m3 += v.volumeM3();
        }
        int back = clock + (at == 0 ? 0 : travel.seconds(at, 0));
        long metersBack = meters + (at == 0 ? 0 : travel.meters(at, 0));
        return new Plan(stops, departure, clock, back, meters, metersBack, waitSec, expectedFailures, lateStops,
                excess(kg, capacityKg), excess(m3, capacityM3),
                shiftEndSec == null || visits.isEmpty() ? 0 : Math.max(0, clock - shiftEndSec),
                preferenceViolations());
    }

    /** Regla de hoy: espera a que abra la ventana real. */
    private static int rigidStart(int arrival, FlexibleVisit v) {
        return arrival < DAY ? Math.max(arrival, v.windowStartSec()) : arrival;
    }

    /**
     * Regla flexible: descarga al llegar o espera al comienzo de una hora siguiente, lo que cueste menos
     * entre el riesgo de no entrega y los minutos de espera.
     */
    int bestStart(int arrival, AcceptanceCurve curve) {
        long perWaitSecond = costs.perShiftMinute() + costs.perWaitMinute();
        int best = arrival;
        double bestCost = costs.failedDelivery() * (1 - curve.at(arrival));
        int limit = arrival + costs.maxWaitMinutes() * 60;
        for (int t = (arrival / 3600 + 1) * 3600; t <= limit; t += 3600) {
            double cost = costs.failedDelivery() * (1 - curve.at(t)) + perWaitSecond * (t - arrival) / 60.0;
            if (cost < bestCost - 1e-6) {
                best = t;
                bestCost = cost;
            }
        }
        return best;
    }

    private static double excess(double load, double capacity) {
        return Double.isNaN(capacity) ? 0 : Math.max(0, load - capacity);
    }

    /** Igual que en la ruta de hoy: Primera fuera del bloque inicial y Última fuera del bloque final. */
    int preferenceViolations() {
        int violations = 0;
        boolean seenNonFirst = false;
        for (FlexibleVisit v : visits) {
            if (!v.first()) seenNonFirst = true;
            else if (seenNonFirst) violations++;
        }
        boolean seenNonLast = false;
        for (int i = visits.size() - 1; i >= 0; i--) {
            FlexibleVisit v = visits.get(i);
            if (!v.last()) seenNonLast = true;
            else if (seenNonLast) violations++;
        }
        return violations;
    }

    /** Una parada en el horario: llegada, inicio de la descarga, espera y probabilidad de que reciba a esa hora. */
    public record StopPlan(UUID visitId, int arrivalSec, int serviceStartSec, int waitSec, double probability) {}

    /**
     * Horario y costo de la ruta.
     *
     * @param departureSec  salida de bodega (en el modo flexible, puede ser más tarde que la pedida)
     * @param endSec        término de la última descarga
     * @param returnSec     vuelta a bodega
     * @param meters        metros hasta la última parada (como mide hoy la ruta)
     * @param metersWithReturn metros con la vuelta a bodega
     * @param lateStops     paradas que empiezan después del cierre de su ventana real aprendida
     */
    public record Plan(List<StopPlan> stops, int departureSec, int endSec, int returnSec, long meters,
                       long metersWithReturn, long waitSec, double expectedFailures, int lateStops,
                       double excessKg, double excessM3, int shiftOverrunSec, int preferenceViolations) {

        /** Probabilidad promedio de entregar en primera visita. */
        public double expectedFirstVisitRate() {
            return stops.isEmpty() ? 1 : 1 - expectedFailures / stops.size();
        }

        /** Minutos de jornada: de la salida de bodega a la vuelta. */
        public long shiftMinutes() {
            return (returnSec - departureSec) / 60;
        }
    }
}
