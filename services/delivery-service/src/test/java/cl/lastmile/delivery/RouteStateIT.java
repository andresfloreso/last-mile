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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-17: la ruta del día como entidad. Guarda optimizador y camión, se confirma con un conductor y una
 * ruta confirmada no se rearma ni se reordena.
 *
 * <p>Usa la semilla compartida con los otros IT: al terminar, la ruta de hoy vuelve a borrador (los demás
 * IT la rearman).
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1",
        "routing.optimizer.time-limit=2s",
        "routing.optimizer.unimproved-time-limit=1s"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RouteStateIT {

    private static final String DEPOT_JSON = "\"depotLat\":-33.446393,\"depotLng\":-70.624979";
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

    private JsonNode build(int expectedStatus) throws Exception {
        return call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"%s",%s}""".formatted(today(), DEPOT_JSON)), expectedStatus);
    }

    private JsonNode state(String role, int expectedStatus) throws Exception {
        return call(role, get("/api/routes/state?date=" + today()), expectedStatus);
    }

    private JsonNode changeState(String status, String driverId, String driverName, int expectedStatus) throws Exception {
        String driver = driverId == null ? "" : ",\"driverId\":\"%s\",\"driverName\":\"%s\"".formatted(driverId, driverName);
        return call("DISPATCHER", put("/api/routes/state").content("""
                {"date":"%s","status":"%s"%s}""".formatted(today(), status, driver)), expectedStatus);
    }

    /** La ruta de hoy vuelve a borrador, sin conductor, aunque haya quedado cerrada. */
    private void backToDraft() {
        jdbc.update("""
                UPDATE routes SET status = 'DRAFT', driver_id = NULL, driver_name = NULL, confirmed_at = NULL
                WHERE route_date = ?""", today());
    }

    private static List<String> ids(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.forEach(s -> ids.add(s.get("shipmentId").asText()));
        return ids;
    }

    @Test
    void armarGuardaLaRutaConSuOptimizadorYSuCamion() throws Exception {
        JsonNode built = build(200);

        JsonNode state = state("OPERATOR", 200);
        assertThat(state.get("status").asText()).isEqualTo("DRAFT");
        assertThat(state.get("editable").asBoolean()).isTrue();
        assertThat(state.get("optimizer").asText()).isEqualTo(built.get(0).get("optimizer").asText());
        assertThat(state.get("vehiclePlate").asText()).isEqualTo(built.get(0).get("vehiclePlate").asText());
        assertThat(state.get("driverId").isNull()).isTrue();

        // GET /api/routes ya no devuelve el optimizador ni el camión en null.
        JsonNode saved = call("OPERATOR", get("/api/routes?date=" + today() + "&" + DEPOT), 200);
        assertThat(saved.get(0).get("optimizer").asText()).isEqualTo(state.get("optimizer").asText());
        assertThat(saved.get(0).get("vehiclePlate").asText()).isEqualTo(state.get("vehiclePlate").asText());

        // Cada envío de la ruta apunta a ella.
        String routeId = state.get("id").asText();
        Integer outside = jdbc.queryForObject("""
                SELECT count(*) FROM shipments WHERE scheduled_date = ? AND route_position IS NOT NULL
                AND (route_id IS NULL OR route_id <> ?::uuid)""", Integer.class, today(), routeId);
        assertThat(outside).isZero();
    }

    @Test
    void unaRutaConfirmadaNoSeRearmaNiSeReordenaHastaVolverABorrador() throws Exception {
        List<String> order = ids(build(200));
        String driver = UUID.randomUUID().toString();
        try {
            changeState("CONFIRMED", null, null, 400);
            JsonNode confirmed = changeState("CONFIRMED", driver, "Pedro Conductor", 200);
            assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
            assertThat(confirmed.get("editable").asBoolean()).isFalse();
            assertThat(confirmed.get("driverId").asText()).isEqualTo(driver);
            assertThat(confirmed.get("driverName").asText()).isEqualTo("Pedro Conductor");
            assertThat(confirmed.get("confirmedAt").isNull()).isFalse();

            List<Map<String, Object>> before = jdbc.queryForList(
                    "SELECT id, route_position FROM shipments WHERE scheduled_date = ? ORDER BY id", today());
            build(409);
            List<String> reversed = new ArrayList<>(order);
            Collections.reverse(reversed);
            call("DISPATCHER", put("/api/routes/order").content("""
                    {"date":"%s","order":%s}""".formatted(today(), json.writeValueAsString(reversed))), 409);
            assertThat(jdbc.queryForList("SELECT id, route_position FROM shipments WHERE scheduled_date = ? ORDER BY id", today()))
                    .as("la ruta confirmada no cambia").isEqualTo(before);

            JsonNode draft = changeState("DRAFT", null, null, 200);
            assertThat(draft.get("editable").asBoolean()).isTrue();
            assertThat(draft.get("driverId").isNull()).isTrue();
            build(200);
        } finally {
            backToDraft();
        }
    }

    @Test
    void enCursoYCerradaSoloAvanzan() throws Exception {
        build(200);
        try {
            changeState("IN_PROGRESS", null, null, 409);
            changeState("CONFIRMED", UUID.randomUUID().toString(), "Pedro", 200);
            assertThat(changeState("IN_PROGRESS", null, null, 200).get("status").asText()).isEqualTo("IN_PROGRESS");
            changeState("DRAFT", null, null, 409);
            assertThat(changeState("CLOSED", null, null, 200).get("status").asText()).isEqualTo("CLOSED");
            changeState("NO_EXISTE", null, null, 400);
        } finally {
            backToDraft();
        }
    }

    @Test
    void permisosYFechaSinRuta() throws Exception {
        build(200);
        call("OPERATOR", put("/api/routes/state").content("""
                {"date":"%s","status":"CONFIRMED","driverId":"%s"}""".formatted(today(), UUID.randomUUID())), 403);
        call("DRIVER", get("/api/routes/state?date=" + today()), 403);
        call("DISPATCHER", get("/api/routes/state?date=2031-03-03"), 404);
        call("DISPATCHER", put("/api/routes/state").content("""
                {"date":"2031-03-03","status":"CONFIRMED","driverId":"%s"}""".formatted(UUID.randomUUID())), 404);
    }
}
