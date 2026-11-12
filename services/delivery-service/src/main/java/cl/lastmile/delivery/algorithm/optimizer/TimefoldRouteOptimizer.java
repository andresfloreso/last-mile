package cl.lastmile.delivery.algorithm.optimizer;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * VRPTW de un camión con Timefold Solver (HU-28).
 *
 * <ol>
 *   <li>Arma la ruta con el nearest-neighbor ({@link NearestNeighborOptimizer}): da la ventana real de
 *       cada parada y es la <b>solución inicial</b>. Como el solver solo acepta mejoras de puntaje, la
 *       ruta final nunca queda peor que la línea base en lo que mide {@link RouteConstraints}.</li>
 *   <li>Mejora el orden con búsqueda tabú durante a lo más {@code routing.optimizer.time-limit} (10 s), o hasta que pasa
 *       {@code routing.optimizer.unimproved-time-limit} sin mejorar.</li>
 *   <li>Si el solver falla, o la mejor ruta no cumple la capacidad o la jornada del camión, devuelve el
 *       nearest-neighbor y dice por qué en {@link OptimizedRoute#note()}.</li>
 * </ol>
 */
@Slf4j
@Component
public class TimefoldRouteOptimizer implements RouteOptimizer {

    private final NearestNeighborOptimizer nearestNeighbor;
    private final OsrmRoutingClient osrm;
    private final double fallbackSpeedKmh;
    private final SolverFactory<RoutePlan> solverFactory;

    public TimefoldRouteOptimizer(NearestNeighborOptimizer nearestNeighbor,
                                  OsrmRoutingClient osrm,
                                  @Value("${routing.fallback-speed-kmh:25}") double fallbackSpeedKmh,
                                  @Value("${routing.optimizer.time-limit:10s}") Duration timeLimit,
                                  @Value("${routing.optimizer.unimproved-time-limit:3s}") Duration unimprovedTimeLimit) {
        this.nearestNeighbor = nearestNeighbor;
        this.osrm = osrm;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
        this.solverFactory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(RoutePlan.class)
                .withEntityClasses(TruckRoute.class)
                .withConstraintProviderClass(RouteConstraints.class)
                .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
                .withPhases(localSearch())
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(timeLimit)
                        .withUnimprovedSpentLimit(unimprovedTimeLimit)));
    }

    /**
     * Búsqueda tabú. La solución inicial ya está completa (el nearest-neighbor), así que no hace falta
     * heurística de construcción. Con la búsqueda por defecto (late acceptance) el solver se queda en un
     * óptimo local: cada parada fuera de ventana cuesta un salto grande, y casi cualquier movimiento
     * dentro de una franja apretada atrasa alguna. En el día cargado de la semilla, late acceptance
     * terminaba con 79 km; la tabú, con 70 km y las mismas paradas fuera de ventana (ver
     * docs/plan/hu-28/antes-despues.md).
     */
    static LocalSearchPhaseConfig localSearch() {
        return new LocalSearchPhaseConfig()
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(1).withValueTabuSize(7))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1000));
    }

    @Override
    public OptimizedRoute optimize(RouteProblem problem) {
        List<RouteStop> baseline = nearestNeighbor.optimize(problem).stops();
        try {
            TruckRoute initial = truck(problem, baseline);
            TruckRoute best = baseline.size() < 2 ? initial : solve(initial);
            TruckRoute.Schedule schedule = best.schedule();
            if (!schedule.feasible()) {
                return new OptimizedRoute(baseline, NEAREST_NEIGHBOR, infeasibleNote(problem.vehicle(), schedule));
            }
            return new OptimizedRoute(reorder(baseline, best.getVisits()), VRPTW, null);
        } catch (RuntimeException e) {
            log.warn("El optimizador VRPTW falló; se usa el nearest-neighbor", e);
            return new OptimizedRoute(baseline, NEAREST_NEIGHBOR,
                    "El optimizador falló (" + e.getClass().getSimpleName() + "): se usó el orden por franja y cercanía");
        }
    }

    private TruckRoute solve(TruckRoute initial) {
        Solver<RoutePlan> solver = solverFactory.buildSolver();
        RoutePlan solved = solver.solve(new RoutePlan(initial.getVisits(), List.of(initial)));
        log.info("Ruta VRPTW de {} paradas: puntaje {}", initial.getVisits().size(), solved.getScore());
        return solved.getTrucks().get(0);
    }

    /** La ruta del camión en el orden del nearest-neighbor, con los tiempos y la carga de cada parada. */
    TruckRoute truck(RouteProblem problem, List<RouteStop> baseline) {
        Map<UUID, RouteOptimizer.Stop> stopById = new HashMap<>();
        problem.stops().forEach(s -> stopById.put(s.representative().getId(), s));

        List<double[]> points = new ArrayList<>();
        points.add(new double[]{problem.depotLat(), problem.depotLng()});
        List<Visit> visits = new ArrayList<>();
        for (RouteStop s : baseline) {
            int point = -1;
            if (s.latitude() != null && s.longitude() != null) {
                point = points.size();
                points.add(new double[]{s.latitude(), s.longitude()});
            }
            RouteOptimizer.Stop stop = stopById.get(s.shipmentId());
            visits.add(new Visit(s.shipmentId(), point, s.windowStart().toSecondOfDay(), s.windowEnd().toSecondOfDay(),
                    s.orderPreference(), stop == null ? 0 : stop.weightKg(), stop == null ? 0 : stop.volumeM3(),
                    stop == null ? -1 : stop.serviceSeconds()));
        }
        TravelTimes travel = TravelTimes.of(points, osrm, fallbackSpeedKmh);
        Vehicle v = problem.vehicle();
        return new TruckRoute(v == null ? "sin-camion" : v.plate(), travel,
                problem.departure().toSecondOfDay(), problem.stopMinutes() * 60,
                v == null ? Double.NaN : v.capacityKg(), v == null ? Double.NaN : v.capacityM3(),
                v == null || v.shiftEnd() == null ? null : v.shiftEnd().toSecondOfDay(), visits);
    }

    /** Las paradas del nearest-neighbor en el orden del solver, con su nueva posición. */
    static List<RouteStop> reorder(List<RouteStop> baseline, List<Visit> order) {
        Map<UUID, RouteStop> byId = new HashMap<>();
        baseline.forEach(s -> byId.put(s.shipmentId(), s));
        List<RouteStop> route = new ArrayList<>();
        int position = 1;
        for (Visit v : order) {
            RouteStop s = byId.get(v.id());
            route.add(new RouteStop(s.shipmentId(), s.customerName(), s.address(), position++, s.windowStart(),
                    s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(), s.packageCount(),
                    s.orderPreference(), s.label(), s.latitude(), s.longitude()));
        }
        return route;
    }

    static String infeasibleNote(Vehicle vehicle, TruckRoute.Schedule s) {
        String truck = vehicle == null ? "el camión" : "el camión " + vehicle.plate();
        List<String> reasons = new ArrayList<>();
        if (s.excessKg() > 0) {
            reasons.add(String.format(Locale.ROOT, "la carga (%.0f kg) supera su capacidad (%.0f kg)", s.kg(), vehicle.capacityKg()));
        }
        if (s.excessM3() > 0) {
            reasons.add(String.format(Locale.ROOT, "el volumen (%.2f m³) supera su capacidad (%.2f m³)", s.m3(), vehicle.capacityM3()));
        }
        if (s.shiftOverrunSec() > 0) {
            reasons.add("la ruta termina " + Math.ceilDiv(s.shiftOverrunSec(), 60) + " min después de su jornada (" + vehicle.shiftEnd() + ")");
        }
        return "Sin solución factible para " + truck + ": " + String.join("; ", reasons)
                + ". Se usó el orden por franja y cercanía.";
    }
}
