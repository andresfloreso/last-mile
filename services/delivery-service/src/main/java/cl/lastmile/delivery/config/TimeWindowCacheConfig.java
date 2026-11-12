package cl.lastmile.delivery.config;

import cl.lastmile.delivery.algorithm.TimeWindowProfileCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** HU-12: con {@code time-window.cache.enabled=false} no hay caché en Redis y el perfil se calcula siempre. */
@Configuration
public class TimeWindowCacheConfig {

    @Bean
    @ConditionalOnMissingBean(TimeWindowProfileCache.class)
    TimeWindowProfileCache noTimeWindowProfileCache() {
        return TimeWindowProfileCache.NONE;
    }
}
