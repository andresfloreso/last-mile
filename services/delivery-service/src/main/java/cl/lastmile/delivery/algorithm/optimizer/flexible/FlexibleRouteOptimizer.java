package cl.lastmile.delivery.algorithm.optimizer.flexible;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.TimefoldRouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.TravelTimes;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeclaredWindow;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prototipo de HU-37: ordena la ruta para minimizar el riesgo de no entrega, la jornada, las esperas y
 * los km, en vez de forzar la ventana real ({@link RiskConstraints}). Está detrás de
 * {@code routing.optimizer.objective=risk}, apagado por defecto. Ver docs/plan/analisis-ventanas-flexibles.md.
 *
 * <p>Parte de la ruta del VRPTW de hoy ({@link TimefoldRouteOptimizer}) y la mejora con la misma búsqueda
 * tabú. Si no logra una ruta factible (capacidad o jornada), devuelve la de hoy.
 *
 * <p>Límite conocido: el horario que muestra la app (HU-14) sigue esperando a que abra la ventana real;
 * el horario del prototipo solo se ve en el análisis.
 */
@Slf4j
@Component
public class FlexibleRouteOptimizer implements RouteOptimizer {

    public static final String NOTE = "Prototipo HU-37: ruta por riesgo de no entrega (routing.optimizer.objective=risk).";

    private final TimefoldRouteOptimizer rigid;
    private final TimeWindowLearningService learning;
    private final DeliveryAttemptRepository attempts;
    private final OsrmRoutingClient osrm;
    private final double fallbackSpeedKmh;
    private final RiskCosts costs;
    private final SolverFactory<FlexibleRoutePlan> solverFactory;

    public FlexibleRouteOptimizer(TimefoldRouteOptimizer rigid,
                                  TimeWindowLearningService learning,
                                  DeliveryAttemptRepository attempts,
                                  OsrmRoutingClient osrm,
                                  @Value("${routing.fallback-speed-kmh:25}") double fallbackSpeedKmh,
                                  @Value("${routing.optimizer.time-limit:10s}") Duration timeLimit,
                                  @Value("${routing.optimizer.unimproved-time-limit:3s}") Duration unimprovedTimeLimit,
                                  @Value("${routing.optimizer.risk.failed-delivery:60000}") long failedDelivery,
                                  @Value("${routing.optimizer.risk.per-km:700}") long perKm,
                                  @Value("${routing.optimizer.risk.per-shift-minute:250}") long perShiftMinute,
                                  @Value("${routing.optimizer.risk.per-wait-minute:50}") long perWaitMinute,
                                  @Value("${routing.optimizer.risk.max-wait-minutes:120}") int maxWaitMinutes,
                                  @Value("${routing.optimizer.risk.max-departure-delay-minutes:180}") int maxDepartureDelay) {
        this.rigid = rigid;
        this.learning = learning;
        this.attempts = attempts;
        this.osrm = osrm;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
        this.costs = new RiskCosts(failedDelivery, perKm, perShiftMinute, perWaitMinute, maxWaitMinutes, maxDepartureDelay);
        this.solverFactory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(FlexibleRoutePlan.class)
                .withEntityClasses(FlexibleTruckRoute.class)
                .withConstraintProviderClass(RiskConstraints.class)
                .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
                .withPhases(new LocalSearchPhaseConfig()
                        .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(1).withValueTabuSize(7))
                        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000)))
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(timeLimit)
                        .withUnimprovedSpentLimit(unimprovedTimeLimit)));
    }

    @Override
    public OptimizedRoute optimize(RouteProblem problem) {
        return compare(problem).flexible();
    }

    /**
     * Arma la ruta de hoy y la del prototipo, y mide las dos con la misma curva de probabilidad: la de hoy
     * con su regla (sale a la hora pedida y espera a que abra la ventana real) y la del prototipo con la
     * flexible.
     */
    public Comparison compare(RouteProblem problem) {
        OptimizedRoute today = rigid.optimize(problem);
        FlexibleTruckRoute initial = truck(problem, today.stops());
        FlexibleTruckRoute.Plan todayPlan = initial.plan(FlexibleTruckRoute.Mode.RIGID);
        if (today.stops().size() < 2) {
            return new Comparison(today, todayPlan, today, initial.plan());
        }
        try {
            FlexibleTruckRoute best = solve(initial);
            FlexibleTruckRoute.Plan plan = best.plan();
            if (plan.excessKg() > 0 || plan.excessM3() > 0 || plan.shiftOverrunSec() > 0) {
                return new Comparison(today, todayPlan, today, initial.plan());
            }
            OptimizedRoute flexible = new OptimizedRoute(reorder(today.stops(), best.getVisits()), today.optimizer(),
                    today.note() == null ? NOTE : today.note() + " " + NOTE);
            return new Comparison(today, todayPlan, flexible, plan);
        } catch (RuntimeException e) {
            log.warn("El prototipo de HU-37 falló; se usa la ruta de hoy", e);
            return new Comparison(today, todayPlan, today, initial.plan());
        }
    }

    private FlexibleTruckRoute solve(FlexibleTruckRoute initial) {
        Solver<FlexibleRoutePlan> solver = solverFactory.buildSolver();
        FlexibleRoutePlan solved = solver.solve(new FlexibleRoutePlan(initial.getVisits(), List.of(initial)));
        log.info("Prototipo HU-37, ruta de {} paradas: puntaje {}", initial.getVisits().size(), solved.getScore());
        return solved.getTrucks().get(0);
    }

    /** La ruta en el orden dado, con la curva de probabilidad de cada local. */
    FlexibleTruckRoute truck(RouteProblem problem, List<RouteStop> order) {
        Map<UUID, RouteOptimizer.Stop> stopById = new HashMap<>();
        problem.stops().forEach(s -> stopById.put(s.representative().getId(), s));
        Map<UUID, AcceptanceCurve> curves = new HashMap<>();

        List<double[]> points = new ArrayList<>();
        points.add(new double[]{problem.depotLat(), problem.depotLng()});
        List<FlexibleVisit> visits = new ArrayList<>();
        for (RouteStop s : order) {
            int point = -1;
            if (s.latitude() != null && s.longitude() != null) {
                point = points.size();
                points.add(new double[]{s.latitude(), s.longitude()});
            }
            RouteOptimizer.Stop stop = stopById.get(s.shipmentId());
            Customer customer = stop.representative().getCustomer();
            AcceptanceCurve curve = curves.computeIfAbsent(customer.getId(), id -> AcceptanceCurve.of(
                    learning.learn(id, attempts.findByCustomerId(id)).histogram(),
                    customer.declaredWindow() == null ? DeclaredWindow.NONE : customer.declaredWindow()));
            visits.add(new FlexibleVisit(s.shipmentId(), point, curve, s.windowStart().toSecondOfDay(),
                    s.windowEnd().toSecondOfDay(), s.orderPreference(), stop.weightKg(), stop.volumeM3()));
        }
        TravelTimes travel = TravelTimes.of(points, osrm, fallbackSpeedKmh);
        Vehicle v = problem.vehicle();
        return new FlexibleTruckRoute(v == null ? "sin-camion" : v.plate(), travel,
                problem.departure().toSecondOfDay(), problem.stopMinutes() * 60,
                v == null ? Double.NaN : v.capacityKg(), v == null ? Double.NaN : v.capacityM3(),
                v == null || v.shiftEnd() == null ? null : v.shiftEnd().toSecondOfDay(), costs, visits);
    }

    static List<RouteStop> reorder(List<RouteStop> stops, List<FlexibleVisit> order) {
        Map<UUID, RouteStop> byId = new HashMap<>();
        stops.forEach(s -> byId.put(s.shipmentId(), s));
        List<RouteStop> route = new ArrayList<>();
        int position = 1;
        for (FlexibleVisit v : order) {
            RouteStop s = byId.get(v.id());
            route.add(new RouteStop(s.shipmentId(), s.customerName(), s.address(), position++, s.windowStart(),
                    s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(), s.packageCount(),
                    s.orderPreference(), s.label(), s.latitude(), s.longitude()));
        }
        return route;
    }

    /**
     * La ruta de hoy y la del prototipo, cada una con su horario.
     *
     * @param todayPlan    horario de la ruta de hoy con la regla de hoy, medido con la curva de probabilidad
     * @param flexiblePlan horario del prototipo con la regla flexible
     */
    public record Comparison(OptimizedRoute today, FlexibleTruckRoute.Plan todayPlan,
                             OptimizedRoute flexible, FlexibleTruckRoute.Plan flexiblePlan) {}
}
