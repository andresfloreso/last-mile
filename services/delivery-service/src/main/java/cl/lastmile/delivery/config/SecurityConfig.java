package cl.lastmile.delivery.config;

import cl.lastmile.delivery.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/tracking/**").permitAll() // seguimiento público por trackingCode
                // Ver la ruta guardada (HU-32): también OPERATOR. Armarla, solo ADMIN y DISPATCHER.
                .requestMatchers(HttpMethod.GET, "/api/routes").hasAnyRole("ADMIN", "DISPATCHER", "OPERATOR")
                .requestMatchers("/api/routes/**").hasAnyRole("ADMIN", "DISPATCHER")
                .requestMatchers("/api/shipments/import").hasAnyRole("ADMIN", "DISPATCHER", "OPERATOR")
                // Denegar por defecto (#44): todo lo demás exige un rol interno. CLIENT solo
                // llega a lo público (seguimiento y health); cada controller afina con @PreAuthorize.
                .anyRequest().hasAnyRole("ADMIN", "DISPATCHER", "OPERATOR", "DRIVER")
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
