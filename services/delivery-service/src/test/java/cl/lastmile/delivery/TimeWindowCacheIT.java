package cl.lastmile.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-12: el perfil horario de un local queda en Redis con TTL y registrar un intento lo invalida.
 *
 * <p>Usa la semilla compartida con los otros IT (mismas propiedades que SeedDataIT): borra el intento que
 * registra y devuelve el envío a su estado.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeWindowCacheIT {

    private static final String REASON = "IT-HU-12";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Value("${jwt.secret}") String jwtSecret;

    private UUID shipmentId;
    private UUID customerId;
    private String originalStatus;

    /**
     * Borra el intento por JDBC, sin pasar por la invalidación, así que también borra el perfil de la
     * caché: si no, SeedDataIT (mismo contexto, misma Redis) leería un perfil con el intento borrado.
     */
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM delivery_attempts WHERE rejection_reason = ?", REASON);
        if (shipmentId != null) jdbc.update("UPDATE shipments SET status = ? WHERE id = ?", originalStatus, shipmentId);
        if (customerId != null) redis.delete(key(customerId));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req) {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claims(Map.of("role", "DISPATCHER", "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode timeWindow(UUID customerId) throws Exception {
        String body = mvc.perform(as(get("/api/customers/" + customerId + "/time-window")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private static String key(UUID customerId) {
        return "lastmile:ventana-real:v1:" + customerId;
    }

    @Test
    void elPerfilQuedaEnRedisConTtlYUnIntentoNuevoLoInvalida() throws Exception {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT s.id AS shipment, s.customer_id AS customer, s.status FROM shipments s
                JOIN customers c ON c.id = s.customer_id WHERE c.external_code = 'SEED-C02'
                ORDER BY s.scheduled_date DESC LIMIT 1""");
        shipmentId = (UUID) row.get("shipment");
        originalStatus = (String) row.get("status");
        customerId = (UUID) row.get("customer");
        redis.delete(key(customerId));

        JsonNode first = timeWindow(customerId);

        assertThat(redis.hasKey(key(customerId))).as("el perfil queda en la caché").isTrue();
        Long ttl = redis.getExpire(key(customerId));
        assertThat(ttl).as("con TTL").isPositive();
        assertThat(timeWindow(customerId)).as("la segunda lectura sale de la caché, igual").isEqualTo(first);

        mvc.perform(as(post("/api/shipments/attempts")).content("""
                        {"shipmentId":"%s","customerId":"%s","outcome":"REJECTED_NOT_HOME","rejectionReason":"%s"}"""
                        .formatted(shipmentId, customerId, REASON)))
                .andExpect(status().isCreated());

        assertThat(redis.hasKey(key(customerId))).as("registrar un intento invalida el perfil").isFalse();
        JsonNode after = timeWindow(customerId);
        assertThat(after.get("totalAttempts").asInt()).as("el perfil nuevo cuenta el intento")
                .isEqualTo(first.get("totalAttempts").asInt() + 1);
    }
}
