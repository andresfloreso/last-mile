package cl.lastmile.delivery.geo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

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
}
