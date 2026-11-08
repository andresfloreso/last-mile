package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Arma la secuencia de entrega del día: "cual primero, segundo, etc." tal
 * como se describe en el pantallazo de referencia, y ahora también respeta
 * el control manual "Orden: Primera / Automático / Última" que existe en la
 * app de referencia (Spoke) para cada parada.
 *
 * Estrategia:
 *  1. Las paradas con orderPreference = FIRST van siempre al principio
 *     (entre ellas, ordenadas por ventana horaria/cercanía).
 *  2. Las paradas con orderPreference = LAST van siempre al final.
 *  3. El resto (AUTO) se ordena por:
 *     a. franja horaria aprendida por cliente (TimeWindowLearningService),
 *     b. dentro de una franja, distancia REAL por calle (OSRM) al punto
 *        anterior de la ruta — no línea recta — para que el orden refleje
 *        el recorrido que el camión efectivamente hará.
 *  4. Si OSRM no está disponible (sin red, timeout), se recurre
 *     automáticamente a la fórmula de Haversine como respaldo, sin
 *     interrumpir el cálculo de ruta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

    private final TimeWindowLearningService learningService;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final OsrmRoutingClient osrmRoutingClient;

    public List<RouteStop> buildRoute(List<Shipment> shipments, double depotLat, double depotLng) {
        List<ScoredShipment> all = new ArrayList<>();
        for (Shipment shipment : shipments) {
            CustomerTimeWindowProfile profile = learningService.learn(
                    shipment.getCustomer().getId(), deliveryAttemptRepository);
            all.add(new ScoredShipment(shipment, profile));
        }

        List<ScoredShipment> firstGroup = all.stream()
                .filter(s -> s.shipment.getOrderPreference() == Shipment.OrderPreference.FIRST)
                .toList();
        List<ScoredShipment> lastGroup = all.stream()
                .filter(s -> s.shipment.getOrderPreference() == Shipment.OrderPreference.LAST)
                .toList();
        List<ScoredShipment> autoGroup = new ArrayList<>(all.stream()
                .filter(s -> s.shipment.getOrderPreference() == Shipment.OrderPreference.AUTO)
                .toList());

        // Intenta obtener distancias reales por calle para TODO el conjunto de puntos
        // (depósito + cada cliente) en una sola llamada a OSRM.
        DistanceLookup distanceLookup = buildDistanceLookup(all, depotLat, depotLng);

        List<RouteStop> route = new ArrayList<>();
        int position = 1;

        // 1) Paradas forzadas al inicio ("Primera"), ordenadas por franja horaria.
        List<ScoredShipment> sortedFirst = new ArrayList<>(firstGroup);
        sortedFirst.sort(Comparator.comparingInt(s -> s.profile.recommendedStartHour()));
        double[] cursor = {depotLat, depotLng};
        for (ScoredShipment s : sortedFirst) {
            route.add(toRouteStop(s, position++));
            updateCursor(cursor, s.shipment);
        }

        // 2) Paradas automáticas: franja horaria + nearest-neighbor por distancia real.
        autoGroup.sort(Comparator
                .comparingInt((ScoredShipment s) -> s.profile.recommendedStartHour())
                .thenComparing(s -> s.profile.confidence(), Comparator.reverseOrder()));

        while (!autoGroup.isEmpty()) {
            int targetHourBlock = autoGroup.get(0).profile.recommendedStartHour();
            final double curLat = cursor[0];
            final double curLng = cursor[1];

            ScoredShipment next = autoGroup.stream()
                    .filter(s -> s.profile.recommendedStartHour() == targetHourBlock)
                    .min(Comparator.comparingDouble(s -> distanceLookup.distanceMeters(curLat, curLng, s.shipment)))
                    .orElse(autoGroup.get(0));

            autoGroup.remove(next);
            route.add(toRouteStop(next, position++));
            updateCursor(cursor, next.shipment);
        }

        // 3) Paradas forzadas al final ("Última"), ordenadas por cercanía al último punto.
        List<ScoredShipment> remainingLast = new ArrayList<>(lastGroup);
        while (!remainingLast.isEmpty()) {
            final double curLat = cursor[0];
            final double curLng = cursor[1];
            ScoredShipment next = remainingLast.stream()
                    .min(Comparator.comparingDouble(s -> distanceLookup.distanceMeters(curLat, curLng, s.shipment)))
                    .orElse(remainingLast.get(0));
            remainingLast.remove(next);
            route.add(toRouteStop(next, position++));
            updateCursor(cursor, next.shipment);
        }

        return route;
    }

    private void updateCursor(double[] cursor, Shipment shipment) {
        Double lat = shipment.getCustomer().getLatitude();
        Double lng = shipment.getCustomer().getLongitude();
        if (lat != null && lng != null) {
            cursor[0] = lat;
            cursor[1] = lng;
        }
    }

    private RouteStop toRouteStop(ScoredShipment s, int position) {
        Customer c = s.shipment.getCustomer();
        return new RouteStop(
                s.shipment.getId(),
                c.getFullName(),
                c.getAddress(),
                position,
                LocalTime.of(s.profile.recommendedStartHour(), 0),
                LocalTime.of(s.profile.recommendedEndHour(), 0),
                s.profile.confidence(),
                s.profile.hoursToAvoid(),
                s.shipment.getStopType().name(),
                s.shipment.getPackageCount(),
                s.shipment.getOrderPreference().name(),
                s.shipment.getLabel(),
                c.getLatitude(),
                c.getLongitude()
        );
    }

    /**
     * Construye una tabla de distancias reales (OSRM) para todos los puntos de la
     * ruta en una sola llamada. Si OSRM falla, degrada a Haversine punto a punto
     * sin lanzar excepción — la ruta siempre se calcula.
     */
    private DistanceLookup buildDistanceLookup(List<ScoredShipment> all, double depotLat, double depotLng) {
        List<double[]> points = new ArrayList<>();
        points.add(new double[]{depotLat, depotLng});
        for (ScoredShipment s : all) {
            Double lat = s.shipment.getCustomer().getLatitude();
            Double lng = s.shipment.getCustomer().getLongitude();
            points.add(new double[]{lat != null ? lat : 0.0, lng != null ? lng : 0.0});
        }

        Optional<double[][]> matrix = osrmRoutingClient.distanceMatrix(points);
        if (matrix.isPresent()) {
            log.info("Usando distancias reales por calle (OSRM) para {} puntos", points.size());
            return new DistanceLookup(points, matrix.get());
        }
        log.info("OSRM no disponible: usando distancia en línea recta (Haversine) como respaldo");
        return new DistanceLookup(points, null);
    }

    /** Resuelve distancia entre el punto actual y un shipment, usando OSRM si hay matriz, si no Haversine. */
    private static class DistanceLookup {
        private final List<double[]> points;
        private final double[][] matrix;

        DistanceLookup(List<double[]> points, double[][] matrix) {
            this.points = points;
            this.matrix = matrix;
        }

        double distanceMeters(double fromLat, double fromLng, Shipment shipment) {
            Double lat = shipment.getCustomer().getLatitude();
            Double lng = shipment.getCustomer().getLongitude();
            double toLat = lat != null ? lat : 0.0;
            double toLng = lng != null ? lng : 0.0;

            if (matrix != null) {
                int fromIdx = indexOf(fromLat, fromLng);
                int toIdx = indexOf(toLat, toLng);
                if (fromIdx >= 0 && toIdx >= 0) {
                    double d = matrix[fromIdx][toIdx];
                    if (!Double.isNaN(d)) return d;
                }
            }
            return haversineKm(fromLat, fromLng, toLat, toLng) * 1000.0; // a metros, misma unidad que OSRM
        }

        private int indexOf(double lat, double lng) {
            for (int i = 0; i < points.size(); i++) {
                if (Math.abs(points.get(i)[0] - lat) < 1e-9 && Math.abs(points.get(i)[1] - lng) < 1e-9) return i;
            }
            return -1;
        }

        private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
            final double R = 6371.0;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return R * c;
        }
    }

    private record ScoredShipment(Shipment shipment, CustomerTimeWindowProfile profile) {}

    public record RouteStop(
            java.util.UUID shipmentId,
            String customerName,
            String address,
            int position,
            LocalTime windowStart,
            LocalTime windowEnd,
            double confidence,
            List<Integer> hoursToAvoid,
            String stopType,
            int packageCount,
            String orderPreference,
            String label,
            Double latitude,
            Double longitude
    ) {}
}
