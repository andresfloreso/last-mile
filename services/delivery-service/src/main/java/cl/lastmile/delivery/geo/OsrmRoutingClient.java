package cl.lastmile.delivery.geo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Calcula distancias reales por calle (no línea recta) usando el servicio
 * "table" de OSRM (Open Source Routing Machine), que devuelve una matriz de
 * distancias/duraciones entre todos los puntos consultados en una sola
 * llamada — mucho más preciso que Haversine para decidir el orden de
 * entrega, porque respeta calles, sentidos de tránsito y cortes reales.
 *
 * Si OSRM no responde (sin red, límite de tasa del servidor demo, etc.), el
 * llamador debe hacer fallback a Haversine — nunca se debe bloquear el
 * cálculo de ruta por falta de conectividad externa.
 */
@Slf4j
@Component
public class OsrmRoutingClient {

    private final WebClient webClient;

    public OsrmRoutingClient(@Value("${routing.osrm-url:https://router.project-osrm.org}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                // /route con la geometría de cada paso pesa ~1 MB para 50 paradas; el límite por defecto es 256 KB.
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    /**
     * @param points lista de coordenadas (lat, lng) en el orden en que se quiere la matriz;
     *               el índice 0 se usa típicamente como el depósito/origen.
     * @return matriz [origen][destino] de distancia en metros por calle real, o Optional.empty()
     *         si OSRM no está disponible (el llamador debe hacer fallback a Haversine).
     */
    public Optional<double[][]> distanceMatrix(List<double[]> points) {
        if (points.size() < 2) {
            return Optional.of(new double[points.size()][points.size()]);
        }

        String coordinates = points.stream()
                .map(p -> p[1] + "," + p[0]) // OSRM espera lon,lat
                .collect(Collectors.joining(";"));

        try {
            JsonNode response = webClient.get()
                    .uri("/table/v1/driving/" + coordinates + "?annotations=distance")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(6))
                    .onErrorResume(ex -> {
                        log.warn("OSRM no disponible, se usará distancia en línea recta como respaldo: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response == null || !"Ok".equals(response.path("code").asText())) {
                return Optional.empty();
            }

            JsonNode distancesNode = response.get("distances");
            int n = points.size();
            double[][] matrix = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    JsonNode cell = distancesNode.get(i).get(j);
                    matrix[i][j] = cell.isNull() ? Double.NaN : cell.asDouble();
                }
            }
            return Optional.of(matrix);

        } catch (Exception ex) {
            log.warn("Error inesperado consultando OSRM: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** Tiempos (s) y distancias (m) por calle entre todos los pares de puntos: [origen][destino]. */
    public record Table(double[][] seconds, double[][] meters) {}

    /**
     * Como {@link #distanceMatrix(List)}, con tiempos y distancias (HU-28: el optimizador necesita
     * los dos). Una celda sin ruta queda en NaN.
     *
     * @return la tabla, o Optional.empty() si OSRM no está disponible
     */
    public Optional<Table> table(List<double[]> points) {
        int n = points.size();
        if (n < 2) return Optional.of(new Table(new double[n][n], new double[n][n]));

        String coordinates = points.stream()
                .map(p -> p[1] + "," + p[0]) // OSRM espera lon,lat
                .collect(Collectors.joining(";"));
        try {
            JsonNode response = webClient.get()
                    .uri("/table/v1/driving/" + coordinates + "?annotations=duration,distance")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(6))
                    .onErrorResume(ex -> {
                        log.warn("OSRM no disponible para la tabla de tiempos: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();
            if (response == null || !"Ok".equals(response.path("code").asText())) return Optional.empty();
            return Optional.of(new Table(cells(response.get("durations"), n), cells(response.get("distances"), n)));
        } catch (Exception ex) {
            log.warn("Error inesperado consultando la tabla de OSRM: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static double[][] cells(JsonNode rows, int n) {
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                JsonNode cell = rows.get(i).get(j);
                matrix[i][j] = cell.isNull() ? Double.NaN : cell.asDouble();
            }
        }
        return matrix;
    }

    /** Un tramo de la ruta entre dos puntos consecutivos. {@code path} va como pares (lat, lng). */
    public record Leg(double durationSeconds, double distanceMeters, List<double[]> path) {}

    /**
     * Ruta por calle que pasa por los puntos en el orden dado (servicio "route" de OSRM),
     * con un tramo por cada par de puntos consecutivos (HU-14: hora estimada y polilínea).
     *
     * @param points coordenadas (lat, lng) en el orden de la ruta; el índice 0 es el depósito
     * @return los {@code points.size() - 1} tramos, o Optional.empty() si OSRM no está disponible
     */
    public Optional<List<Leg>> route(List<double[]> points) {
        if (points.size() < 2) {
            return Optional.of(List.of());
        }
        String coordinates = points.stream()
                .map(p -> p[1] + "," + p[0]) // OSRM espera lon,lat
                .collect(Collectors.joining(";"));
        try {
            JsonNode response = webClient.get()
                    .uri("/route/v1/driving/" + coordinates + "?overview=false&steps=true&geometries=geojson")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(8))
                    .onErrorResume(ex -> {
                        log.warn("OSRM /route no disponible, se estimarán los tiempos: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response == null || !"Ok".equals(response.path("code").asText())) {
                return Optional.empty();
            }
            JsonNode legsNode = response.path("routes").path(0).path("legs");
            if (!legsNode.isArray() || legsNode.size() != points.size() - 1) {
                log.warn("OSRM /route devolvió {} tramos para {} puntos", legsNode.size(), points.size());
                return Optional.empty();
            }
            List<Leg> legs = new ArrayList<>();
            for (JsonNode leg : legsNode) {
                List<double[]> path = new ArrayList<>();
                for (JsonNode step : leg.path("steps")) {
                    for (JsonNode coord : step.path("geometry").path("coordinates")) {
                        double[] point = {coord.get(1).asDouble(), coord.get(0).asDouble()}; // a (lat, lng)
                        double[] last = path.isEmpty() ? null : path.get(path.size() - 1);
                        if (last == null || last[0] != point[0] || last[1] != point[1]) path.add(point);
                    }
                }
                legs.add(new Leg(leg.path("duration").asDouble(), leg.path("distance").asDouble(), path));
            }
            return Optional.of(legs);
        } catch (Exception ex) {
            log.warn("Error inesperado consultando OSRM /route: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
