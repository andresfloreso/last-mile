package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.optimizer.UnloadTime;
import cl.lastmile.delivery.dto.DeliveryDtos.PlannedStopResponse;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.geo.OsrmRoutingClient.Leg;
import cl.lastmile.delivery.service.StopConsolidator.StopLoad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hora estimada por parada (HU-14). Es una capa aparte del optimizador: recibe el
 * orden que ya decidió {@code RouteOptimizationService} (con las paradas "Primera"
 * y "Última" en su lugar) y no lo cambia; solo le pone horario.
 *
 * <p>Para cada parada, en orden:
 * <ol>
 *   <li><b>llegada</b> = salida de la parada anterior (o del depósito) + tiempo del tramo;</li>
 *   <li><b>inicio de la entrega</b> = la llegada, o la apertura de la ventana real si el camión llega
 *       antes (espera: llegar con el local cerrado es justo lo que se quiere evitar);</li>
 *   <li><b>salida</b> = inicio + tiempo de descarga: base + minutos por bulto + ajuste del local
 *       (HU-15, {@link UnloadTime}).</li>
 * </ol>
 * Una parada queda <b>fuera de su ventana real</b> si la entrega empezaría a la hora de cierre o después.
 * Si la ruta no alcanza en el día, las horas siguen corriendo y {@code dayOffset} indica el día
 * siguiente (esas paradas quedan fuera de ventana: la ventana es del día programado).
 *
 * <p>Los tramos salen de OSRM (/route, por calle y con la geometría para el mapa). Si OSRM no
 * responde, se estiman con la distancia en línea recta × {@value #DETOUR_FACTOR} a una velocidad
 * urbana configurable, y la polilínea une las paradas en línea recta.
 */
@Service
public class RouteTimingService {

    /** Las calles no van en línea recta: factor típico para ciudad. */
    static final double DETOUR_FACTOR = 1.3;
    private static final int DAY = 24 * 3600;

    private final OsrmRoutingClient osrm;
    private final double fallbackSpeedKmh;

    public RouteTimingService(OsrmRoutingClient osrm,
                              @Value("${routing.fallback-speed-kmh:25}") double fallbackSpeedKmh) {
        this.osrm = osrm;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
    }

    public List<PlannedStopResponse> schedule(List<RouteStop> route, double depotLat, double depotLng,
                                              LocalTime departure, int stopMinutes) {
        return schedule(route, Map.of(), depotLat, depotLng, departure, stopMinutes);
    }

    /** Con el mismo tiempo de descarga en todas las paradas (HU-14), más el ajuste de cada local. */
    public List<PlannedStopResponse> schedule(List<RouteStop> route, Map<UUID, StopLoad> loads,
                                              double depotLat, double depotLng,
                                              LocalTime departure, int stopMinutes) {
        return plan(route, loads, depotLat, depotLng, departure, UnloadTime.fixed(stopMinutes), false).stops();
    }

    /** Con el tiempo de descarga de HU-15 (base + bultos + ajuste del local). */
    public List<PlannedStopResponse> schedule(List<RouteStop> route, Map<UUID, StopLoad> loads,
                                              double depotLat, double depotLng,
                                              LocalTime departure, UnloadTime unload) {
        return plan(route, loads, depotLat, depotLng, departure, unload, false).stops();
    }

    /**
     * La ruta con horario y, si se pidió, la vuelta a la bodega desde la última parada (HU-15: km
     * totales y hora de vuelta).
     *
     * @param returnLeg null si no se pidió o la ruta no tiene paradas con coordenadas
     */
    public record TimedRoute(List<PlannedStopResponse> stops, ReturnLeg returnLeg) {}

    /**
     * @param arrival   hora de llegada a la bodega
     * @param dayOffset 1 si llega al día siguiente
     */
    public record ReturnLeg(LocalTime arrival, int dayOffset, int travelMinutes, int distanceMeters,
                            List<double[]> path) {}

    /**
     * Horario de la ruta, con lo que se entrega en cada parada consolidada (HU-30: envíos; HU-29: peso y
     * volumen; HU-15: ajuste de descarga del local), por id del envío representante. Una parada sin
     * entrada tiene un solo envío, sin peso ni volumen conocidos y sin ajuste.
     *
     * @param withReturn agrega la vuelta a la bodega desde la última parada con coordenadas
     */
    public TimedRoute plan(List<RouteStop> route, Map<UUID, StopLoad> loads,
                           double depotLat, double depotLng,
                           LocalTime departure, UnloadTime unload, boolean withReturn) {
        // Solo las paradas con coordenadas entran al recorrido; las demás se informan sin tramo.
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{depotLat, depotLng});
        for (RouteStop stop : route) {
            if (located(stop)) points.add(new double[]{stop.latitude(), stop.longitude()});
        }
        boolean returns = withReturn && points.size() > 1;
        if (returns) points.add(new double[]{depotLat, depotLng});

        Optional<List<Leg>> osrmLegs = osrm.route(points);
        List<Leg> legs = osrmLegs.orElseGet(() -> estimatedLegs(points));
        String source = osrmLegs.isPresent() ? "OSRM" : "ESTIMATED";

        List<PlannedStopResponse> planned = new ArrayList<>();
        int clock = departure.toSecondOfDay();
        int legIndex = 0;
        for (RouteStop stop : route) {
            Leg leg = null;
            if (located(stop)) leg = legs.get(legIndex++);

            int travel = leg == null ? 0 : (int) Math.round(leg.durationSeconds());
            int arrival = clock + travel;
            int windowStart = stop.windowStart().toSecondOfDay();
            int windowEnd = stop.windowEnd().toSecondOfDay();
            int serviceStart = arrival < DAY ? Math.max(arrival, windowStart) : arrival;
            StopLoad load = loads.getOrDefault(stop.shipmentId(), StopLoad.single(stop.shipmentId()));
            int service = unload.seconds(stop.packageCount(), load.unloadExtraMinutes());
            int leave = serviceStart + service;

            planned.add(PlannedStopResponse.of(
                    stop,
                    time(arrival),
                    time(serviceStart),
                    time(leave),
                    minutes(serviceStart - arrival),
                    leg == null ? null : minutes(travel),
                    leg == null ? null : (int) Math.round(leg.distanceMeters()),
                    serviceStart >= windowEnd,
                    leg == null ? List.of() : leg.path(),
                    leg == null ? "NO_LOCATION" : source,
                    Math.floorDiv(arrival, DAY),
                    load,
                    minutes(service)));
            clock = leave;
        }
        ReturnLeg back = null;
        if (returns) {
            Leg leg = legs.get(legIndex);
            int travel = (int) Math.round(leg.durationSeconds());
            int arrival = clock + travel;
            back = new ReturnLeg(time(arrival), Math.floorDiv(arrival, DAY), minutes(travel),
                    (int) Math.round(leg.distanceMeters()), leg.path());
        }
        return new TimedRoute(planned, back);
    }

    /** Tramos estimados sin OSRM: línea recta × factor de desvío, a velocidad urbana. */
    List<Leg> estimatedLegs(List<double[]> points) {
        List<Leg> legs = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            double meters = haversineMeters(a[0], a[1], b[0], b[1]) * DETOUR_FACTOR;
            double seconds = meters / (fallbackSpeedKmh * 1000.0 / 3600.0);
            legs.add(new Leg(seconds, meters, List.of(a, b)));
        }
        return legs;
    }

    private static boolean located(RouteStop stop) {
        return stop.latitude() != null && stop.longitude() != null;
    }

    /** Segundos desde la medianoche del día programado → hora del día (el día lo indica dayOffset). */
    private static LocalTime time(int secondOfDay) {
        return LocalTime.ofSecondOfDay(Math.floorMod(secondOfDay, DAY));
    }

    private static int minutes(int seconds) {
        return (int) Math.round(seconds / 60.0);
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
