package cl.lastmile.delivery.geo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Geocodifica direcciones con Nominatim (OpenStreetMap, el mismo dataset que
 * dibuja Leaflet en el frontend). HU-09:
 * <ul>
 *   <li><b>1 request por segundo:</b> todas las consultas pasan por un
 *       {@link RateLimiter} compartido (política de uso de Nominatim).</li>
 *   <li><b>Caché por dirección:</b> una dirección repetida (normalizada) no
 *       vuelve a consultar Nominatim. También se cachea "sin resultados"; los
 *       errores de red no, para reintentar la próxima vez.</li>
 *   <li><b>Confianza:</b> un resultado es preciso si Nominatim lo ubicó a nivel
 *       de número de calle o edificio ({@code place_rank >= min-place-rank}).
 *       Si solo encontró la calle, el barrio o la comuna, el pin es aproximado
 *       y el local queda marcado "revisar".</li>
 * </ul>
 * Solo se usa cuando la fila o el formulario no traen latitud/longitud: las
 * coordenadas entregadas por el usuario tienen prioridad y nunca se pisan.
 */
@Slf4j
@Service
public class GeocodingService {

    private final WebClient webClient;
    private final RateLimiter rateLimiter;
    private final int minPlaceRank;
    private final Map<String, Optional<GeoPoint>> cache;

    public GeocodingService(
            @Value("${geocoding.nominatim-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${geocoding.user-agent:LastMilePlatform/1.0 (contacto@lastmile.cl)}") String userAgent,
            @Value("${geocoding.min-interval:1s}") Duration minInterval,
            @Value("${geocoding.min-place-rank:28}") int minPlaceRank,
            @Value("${geocoding.cache-size:10000}") int cacheSize) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                // Nominatim exige un User-Agent identificable (política de uso).
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.rateLimiter = new RateLimiter(minInterval);
        this.minPlaceRank = minPlaceRank;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Optional<GeoPoint>> eldest) {
                return size() > cacheSize;
            }
        });
    }

    public Optional<GeoPoint> geocode(String address, String commune) {
        String query = commune != null && !commune.isBlank()
                ? address.trim() + ", " + commune.trim() + ", Chile"
                : address.trim() + ", Chile";
        String key = cacheKey(query);

        Optional<GeoPoint> cached = cache.get(key);
        if (cached != null) return cached;

        try {
            return rateLimiter.call(() -> {
                // Otra petición pudo resolver la misma dirección mientras se esperaba turno.
                Optional<GeoPoint> again = cache.get(key);
                if (again != null) return again;
                return query(query).map(response -> {
                    Optional<GeoPoint> result = parse(response);
                    cache.put(key, result);
                    return result;
                }).orElse(Optional.empty());
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Consulta Nominatim; vacío si falló la red o el servidor (y entonces no se cachea). */
    private Optional<JsonNode> query(String query) {
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", "cl")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return Optional.ofNullable(response);
        } catch (Exception ex) {
            // Un corte de red no debe dejar la dirección como "sin resultados".
            log.warn("Geocodificación falló para '{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeoPoint> parse(JsonNode response) {
        if (response == null || !response.isArray() || response.isEmpty()) return Optional.empty();
        JsonNode first = response.get(0);
        if (!first.hasNonNull("lat") || !first.hasNonNull("lon")) return Optional.empty();

        double lat = first.get("lat").asDouble();
        double lon = first.get("lon").asDouble();
        double importance = first.path("importance").asDouble(0.5);
        int placeRank = first.path("place_rank").asInt(0);
        return Optional.of(new GeoPoint(lat, lon, importance, placeRank >= minPlaceRank));
    }

    /** Normaliza la consulta: mayúsculas, espacios repetidos y bordes no cuentan como otra dirección. */
    static String cacheKey(String query) {
        return query.trim().replaceAll("\\s+", " ").replace(" ,", ",").toLowerCase(Locale.ROOT);
    }

    /**
     * @param confidence {@code importance} de Nominatim (0..1), informativo.
     * @param precise    true si se ubicó a nivel de número o edificio; false = revisar.
     */
    public record GeoPoint(double latitude, double longitude, double confidence, boolean precise) {}

    /**
     * Una consulta a la vez y al menos {@code minInterval} entre el fin de una
     * y el inicio de la siguiente: así, aunque la red demore, Nominatim nunca
     * recibe dos requests con menos de ese intervalo.
     */
    static final class RateLimiter {
        private final long intervalNanos;
        private long nextAllowed;

        RateLimiter(Duration minInterval) {
            this.intervalNanos = minInterval.toNanos();
            this.nextAllowed = System.nanoTime();
        }

        synchronized <T> T call(java.util.function.Supplier<T> request) throws InterruptedException {
            long wait = nextAllowed - System.nanoTime();
            if (wait > 0) Thread.sleep(Duration.ofNanos(wait));
            try {
                return request.get();
            } finally {
                nextAllowed = System.nanoTime() + intervalNanos;
            }
        }
    }
}
