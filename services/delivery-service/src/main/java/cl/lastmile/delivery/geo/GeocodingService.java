package cl.lastmile.delivery.geo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * Geocodifica direcciones a coordenadas precisas usando Nominatim (el motor
 * de búsqueda de OpenStreetMap — el mismo dataset que usa Leaflet para
 * dibujar el mapa en el frontend, por lo que el marcador y la dirección
 * quedan alineados con el mismo sistema de referencia).
 *
 * Se usa SOLO cuando el cliente no envía latitud/longitud explícitas (p.ej.
 * import masivo desde una planilla sin columnas de coordenadas): si el
 * usuario ya entregó lat/lng exactas (desde el mapa del frontend), esas
 * tienen prioridad y nunca se sobreescriben.
 */
@Slf4j
@Service
public class GeocodingService {

    private final WebClient webClient;

    public GeocodingService(@Value("${geocoding.nominatim-url:https://nominatim.openstreetmap.org}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                // Nominatim exige un User-Agent identificable — es política de uso obligatoria.
                .defaultHeader("User-Agent", "LastMilePlatform/1.0 (contacto@lastmile.cl)")
                .build();
    }

    public Optional<GeoPoint> geocode(String address, String commune) {
        String query = commune != null && !commune.isBlank() ? address + ", " + commune + ", Chile" : address + ", Chile";

        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", "cl")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(ex -> {
                        log.warn("Geocoding falló para '{}': {}", query, ex.getMessage());
                        return Mono.empty();
                    })
                    .block();

            if (response == null || !response.isArray() || response.isEmpty()) {
                return Optional.empty();
            }

            JsonNode first = response.get(0);
            double lat = first.get("lat").asDouble();
            double lon = first.get("lon").asDouble();
            // "importance" de Nominatim (0..1) se usa como proxy de confianza del match.
            double importance = first.has("importance") ? first.get("importance").asDouble() : 0.5;

            return Optional.of(new GeoPoint(lat, lon, importance));

        } catch (Exception ex) {
            log.warn("Error inesperado geocodificando '{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    public record GeoPoint(double latitude, double longitude, double confidence) {}
}
