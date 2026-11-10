package cl.lastmile.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-32: GET /api/routes devuelve la ruta guardada sin recalcular ni escribir, y OPERATOR la ve.
 *
 * <p>Usa la semilla compartida con los otros IT: arma la ruta de hoy (como RoutePlanIT) y deja cada
 * envío como estaba.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SavedRouteIT {

    private static final String DEPOT = "depotLat=-33.446393&depotLng=-70.624979";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Value("${jwt.secret}") String jwtSecret;

    private MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder req) {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claims(Map.of("role", role, "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode call(String role, MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        String content = mvc.perform(as(role, req)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isEmpty() ? null : json.readTree(content);
    }

    private LocalDate today() {
        return jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
    }

    private JsonNode build() throws Exception {
        return call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.446393,"depotLng":-70.624979}""".formatted(today())), 200);
    }

    private JsonNode saved(String role, LocalDate date) throws Exception {
        return call(role, get("/api/routes?date=" + date + "&" + DEPOT), 200);
    }

    /** Posición y estado de cada envío de la fecha: lo que el GET no debe tocar. */
    private List<Map<String, Object>> snapshot(LocalDate date) {
        return jdbc.queryForList("SELECT id, route_position, status FROM shipments WHERE scheduled_date = ? ORDER BY id", date);
    }

    private static List<String> ids(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.forEach(s -> ids.add(s.get("shipmentId").asText()));
        return ids;
    }

    @Test
    void devuelveLaRutaGuardadaConSuHoraEstimadaSinEscribir() throws Exception {
        JsonNode built = build();
        List<Map<String, Object>> before = snapshot(today());

        JsonNode route = saved("DISPATCHER", today());

        assertThat(snapshot(today())).as("el GET no cambia posición ni estado").isEqualTo(before);
        assertThat(ids(route)).containsExactlyElementsOf(ids(built));
        for (int i = 0; i < route.size(); i++) {
            JsonNode got = route.get(i);
            JsonNode exp = built.get(i);
            assertThat(got.get("position").asInt()).isEqualTo(exp.get("position").asInt());
            assertThat(got.get("serviceStart").asText()).isEqualTo(exp.get("serviceStart").asText());
            assertThat(got.get("outsideWindow").asBoolean()).isEqualTo(exp.get("outsideWindow").asBoolean());
            assertThat(got.get("windowStart").asText()).isEqualTo(exp.get("windowStart").asText());
            assertThat(got.get("shipmentIds")).isEqualTo(exp.get("shipmentIds"));
            assertThat(got.get("weightKg").asDouble()).isEqualTo(exp.get("weightKg").asDouble());
            assertThat(got.get("optimizer").isNull()).as("el optimizador no se guarda").isTrue();
        }
    }

    @Test
    void unEnvioSinPosicionNoEstaEnLaRutaHastaVolverAArmarla() throws Exception {
        build();
        // Un local con un solo envío hoy: sin posición, como si se hubiera importado después de armar.
        Map<String, Object> lone = jdbc.queryForMap("""
                SELECT s.id, s.route_position FROM shipments s
                WHERE s.scheduled_date = ? AND (SELECT count(*) FROM shipments o
                      WHERE o.customer_id = s.customer_id AND o.scheduled_date = s.scheduled_date) = 1
                ORDER BY s.route_position LIMIT 1""", today());
        jdbc.update("UPDATE shipments SET route_position = NULL WHERE id = ?", lone.get("id"));
        try {
            JsonNode route = saved("DISPATCHER", today());
            assertThat(route).hasSize(23);
            assertThat(ids(route)).doesNotContain(lone.get("id").toString());
        } finally {
            jdbc.update("UPDATE shipments SET route_position = ? WHERE id = ?", lone.get("route_position"), lone.get("id"));
        }
    }

    @Test
    void unaFechaSinRutaArmadaDevuelveUnaListaVacia() throws Exception {
        assertThat(saved("DISPATCHER", LocalDate.of(2031, 3, 3))).isEmpty();
    }

    @Test
    void operatorVeLaRutaPeroNoLaArma() throws Exception {
        build();
        assertThat(saved("OPERATOR", today())).hasSize(24);
        call("OPERATOR", post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.446393,"depotLng":-70.624979}""".formatted(today())), 403);
        call("DRIVER", get("/api/routes?date=" + today() + "&" + DEPOT), 403);
        call("CLIENT", get("/api/routes?date=" + today() + "&" + DEPOT), 403);
        assertThat(saved("ADMIN", today())).hasSize(24);
    }

    @Test
    void validaLosParametros() throws Exception {
        call("DISPATCHER", get("/api/routes?" + DEPOT), 400);
        call("DISPATCHER", get("/api/routes?date=" + today()), 400);
        call("DISPATCHER", get("/api/routes?date=" + today() + "&" + DEPOT + "&stopMinutes=0"), 400);
        call("DISPATCHER", get("/api/routes?date=hoy&" + DEPOT), 400);
    }
}
