package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * Caché del perfil horario de cada local (HU-12). La implementación de producción guarda el perfil en
 * Redis con TTL ({@code cache.RedisTimeWindowProfileCache}); registrar un intento de entrega lo invalida.
 *
 * <p>Una falla de la caché nunca bloquea el cálculo: si no responde, el perfil se calcula de nuevo.
 */
public interface TimeWindowProfileCache {

    Optional<CustomerTimeWindowProfile> get(UUID customerId);

    void put(CustomerTimeWindowProfile profile);

    void evict(UUID customerId);

    /** Sin caché: siempre calcula. La usan los tests unitarios y {@code time-window.cache.enabled=false}. */
    TimeWindowProfileCache NONE = new TimeWindowProfileCache() {
        @Override public Optional<CustomerTimeWindowProfile> get(UUID customerId) { return Optional.empty(); }
        @Override public void put(CustomerTimeWindowProfile profile) { }
        @Override public void evict(UUID customerId) { }
    };
}
