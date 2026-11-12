package cl.lastmile.delivery.cache;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.algorithm.TimeWindowProfileCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Perfil horario de cada local en Redis, con TTL (HU-12).
 *
 * <p>Clave {@code lastmile:ventana-real:v1:<customerId>}, valor JSON del perfil. Si Redis no responde, se
 * registra un aviso y el perfil se calcula de nuevo: la ruta nunca espera a la caché.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "time-window.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RedisTimeWindowProfileCache implements TimeWindowProfileCache {

    static final String PREFIX = "lastmile:ventana-real:v1:";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public RedisTimeWindowProfileCache(StringRedisTemplate redis, ObjectMapper json,
                                       @Value("${time-window.cache.ttl:6h}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    static String key(UUID customerId) {
        return PREFIX + customerId;
    }

    @Override
    public Optional<CustomerTimeWindowProfile> get(UUID customerId) {
        try {
            String value = redis.opsForValue().get(key(customerId));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, CustomerTimeWindowProfile.class));
        } catch (Exception ex) {
            log.warn("Caché de ventana real no disponible, se calcula el perfil: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(CustomerTimeWindowProfile profile) {
        try {
            redis.opsForValue().set(key(profile.customerId()), json.writeValueAsString(profile), ttl);
        } catch (Exception ex) {
            log.warn("No se pudo guardar el perfil en la caché de ventana real: {}", ex.getMessage());
        }
    }

    @Override
    public void evict(UUID customerId) {
        try {
            redis.delete(key(customerId));
        } catch (Exception ex) {
            log.warn("No se pudo invalidar el perfil en la caché de ventana real: {}", ex.getMessage());
        }
    }

    /**
     * Al arrancar, borra los perfiles guardados: la semilla (Flyway afterMigrate) o una migración pudieron
     * cambiar los intentos sin pasar por {@link #evict}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void clearOnStartup() {
        try {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(500).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) redis.delete(keys);
            log.info("Caché de ventana real: {} perfiles borrados al arrancar", keys.size());
        } catch (Exception ex) {
            log.warn("No se pudo limpiar la caché de ventana real al arrancar: {}", ex.getMessage());
        }
    }
}
