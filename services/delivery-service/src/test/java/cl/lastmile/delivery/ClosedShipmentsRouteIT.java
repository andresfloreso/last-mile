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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #70: armar la ruta solo toma envíos PENDING o ROUTED y nunca cambia el estado de un envío cerrado.
 *
 * <p>Usa la semilla compartida con los otros IT (mismas propiedades que SeedDataIT): arma la ruta de un
 * día pasado de la semilla, que solo tiene envíos entregados o reagendados, más un envío pendiente que
 * el test crea y borra al terminar.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClosedShipmentsRouteIT {

    private static final String TRACKING = "IT-70-PENDIENTE";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Value("${jwt.secret}") String jwtSecret;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM shipments WHERE tracking_code = ?", TRACKING);
    }

    private String token() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claims(Map.of("role", "DISPATCHER", "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private JsonNode build(LocalDate date) throws Exception {
        String body = mvc.perform(post("/api/routes/build")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","depotLat":-33.446393,"depotLng":-70.624979}""".formatted(date)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    /** Estado y posición de los envíos cerrados de la fecha: lo que armar la ruta no debe tocar. */
    private List<Map<String, Object>> closed(LocalDate date) {
        return jdbc.queryForList("""
                SELECT id, status, route_position FROM shipments
                WHERE scheduled_date = ? AND status NOT IN ('PENDING', 'ROUTED') ORDER BY id""", date);
    }

    private static List<String> shipmentIds(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.forEach(stop -> stop.get("shipmentIds").forEach(id -> ids.add(id.asText())));
        return ids;
    }

    @Test
    void armarLaRutaDeUnDiaPasadoNoTocaLosEnviosCerrados() throws Exception {
        // El día pasado más reciente de la semilla que tiene envíos entregados.
        LocalDate day = jdbc.queryForObject("""
                SELECT max(scheduled_date) FROM shipments
                WHERE scheduled_date < CURRENT_DATE AND status = 'DELIVERED'""", LocalDate.class);
        assertThat(day).as("la semilla trae historial con envíos entregados").isNotNull();
        List<Map<String, Object>> before = closed(day);
        assertThat(before).extracting(r -> r.get("status")).contains("DELIVERED");

        // Un envío que todavía se puede repartir ese día, para un local que no tiene otro envío esa fecha.
        UUID customer = jdbc.queryForObject("""
                SELECT c.id FROM customers c WHERE c.external_code LIKE 'SEED-C%'
                  AND NOT EXISTS (SELECT 1 FROM shipments s WHERE s.customer_id = c.id AND s.scheduled_date = ?)
                ORDER BY c.external_code LIMIT 1""", UUID.class, day);
        UUID pending = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date, status)
                VALUES (?, ?, ?, ?, 'PENDING')""", pending, TRACKING, customer, day);

        JsonNode route = build(day);

        assertThat(shipmentIds(route)).as("la ruta solo trae el envío pendiente").containsExactly(pending.toString());
        assertThat(closed(day)).as("los envíos cerrados conservan su estado y su posición").isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM shipments WHERE id = ?", String.class, pending))
                .isEqualTo("ROUTED");
    }
}
