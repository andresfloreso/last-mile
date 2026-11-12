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
 * HU-15: armado manual de la ruta. Vista previa sin escribir, reoptimizar respetando las paradas fijadas,
 * guardar el orden y el ajuste de descarga por local.
 *
 * <p>Usa la semilla compartida con los otros IT: arma la ruta de hoy y, al terminar, deja el orden y el
 * ajuste de cada local como estaban.
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
class RouteEditIT {

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

    private JsonNode build() throws Exception {
        return call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"%s",%s}""".formatted(today(), DEPOT_JSON)), 200);
    }

    private JsonNode preview(List<String> order, List<String> pinned, boolean reoptimize, int expectedStatus) throws Exception {
        return call("DISPATCHER", post("/api/routes/preview").content("""
                {"date":"%s",%s,"order":%s,"pinned":%s,"reoptimize":%s}""".formatted(
                today(), DEPOT_JSON, json.writeValueAsString(order), json.writeValueAsString(pinned), reoptimize)),
                expectedStatus);
    }

    private void saveOrder(List<String> order, int expectedStatus) throws Exception {
        call("DISPATCHER", put("/api/routes/order").content("""
                {"date":"%s","order":%s}""".formatted(today(), json.writeValueAsString(order))), expectedStatus);
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("SELECT id, route_position, status FROM shipments WHERE scheduled_date = ? ORDER BY id", today());
    }

    private static List<String> ids(JsonNode route) {
        List<String> ids = new ArrayList<>();
        route.forEach(s -> ids.add(s.get("shipmentId").asText()));
        return ids;
    }

    @Test
    void laVistaPreviaDelOrdenGuardadoCoincideConLaRutaYNoEscribe() throws Exception {
        JsonNode built = build();
        List<Map<String, Object>> before = snapshot();

        JsonNode same = preview(ids(built), List.of(), false, 200);
        List<String> reversed = new ArrayList<>(ids(built));
        Collections.reverse(reversed);
        JsonNode moved = preview(reversed, List.of(), false, 200);

        assertThat(snapshot()).as("la vista previa no escribe").isEqualTo(before);
        assertThat(ids(same.get("stops"))).containsExactlyElementsOf(ids(built));
        for (int i = 0; i < built.size(); i++) {
            assertThat(same.get("stops").get(i).get("serviceStart").asText()).isEqualTo(built.get(i).get("serviceStart").asText());
        }
        assertThat(ids(moved.get("stops"))).containsExactlyElementsOf(reversed);
        assertThat(moved.get("stops").get(0).get("position").asInt()).isEqualTo(1);

        JsonNode summary = same.get("summary");
        int legs = 0;
        for (JsonNode s : same.get("stops")) legs += s.get("legDistanceMeters").asInt();
        assertThat(summary.get("distanceMeters").asInt()).as("incluye la vuelta a la bodega").isGreaterThan(legs);
        assertThat(summary.get("returnTime").asText()).matches("\\d{2}:\\d{2}");
        assertThat(summary.get("returnPath")).isNotEmpty();
        assertThat(moved.get("summary").get("outsideWindowStops").asInt())
                .as("al revés, los locales de la mañana quedan fuera de su ventana")
                .isGreaterThan(summary.get("outsideWindowStops").asInt());
    }

    @Test
    void elTiempoDeDescargaEsBaseMasBultosMasElAjusteDelLocal() throws Exception {
        JsonNode built = build();
        for (JsonNode s : built) {
            double expected = 8 + 1.5 * s.get("packageCount").asInt() + s.get("unloadExtraMinutes").asInt();
            assertThat(s.get("serviceMinutes").asInt()).as(s.get("customerName").asText())
                    .isEqualTo((int) Math.round(expected));
        }
        JsonNode santaRosa = null;
        for (JsonNode s : built) if (s.get("customerName").asText().equals("Botillería Santa Rosa")) santaRosa = s;
        assertThat(santaRosa).isNotNull();
        assertThat(santaRosa.get("unloadExtraMinutes").asInt()).isEqualTo(10);
        assertThat(santaRosa.get("unloadNote").asText()).isEqualTo("estacionamiento difícil");
    }

    @Test
    void reoptimizarDejaLasParadasFijadasEnSuPosicion() throws Exception {
        List<String> system = ids(build());
        // Las paradas 1 y 6 del sistema quedan fijadas en su lugar; las demás se desordenan (al revés).
        List<String> shuffled = new ArrayList<>(system);
        List<String> free = new ArrayList<>(system);
        free.remove(system.get(0));
        free.remove(system.get(5));
        Collections.reverse(free);
        for (int i = 0, k = 0; i < shuffled.size(); i++) {
            if (i != 0 && i != 5) shuffled.set(i, free.get(k++));
        }
        List<String> pinned = List.of(system.get(0), system.get(5));

        JsonNode result = preview(shuffled, pinned, true, 200);

        List<String> order = ids(result.get("stops"));
        assertThat(order).hasSameSizeAs(system).containsExactlyInAnyOrderElementsOf(system);
        assertThat(order.get(0)).isEqualTo(system.get(0));
        assertThat(order.get(5)).isEqualTo(system.get(5));
        assertThat(result.get("optimizer").asText()).isIn("VRPTW", "NEAREST_NEIGHBOR");
        assertThat(result.get("summary").get("outsideWindowStops").asInt())
                .isLessThan(preview(shuffled, List.of(), false, 200).get("summary").get("outsideWindowStops").asInt());
    }

    @Test
    void guardarPersisteElOrdenYElGetLoDevuelve() throws Exception {
        JsonNode built = build();
        List<String> original = ids(built);
        List<String> swapped = new ArrayList<>(original);
        Collections.swap(swapped, 0, 1);
        try {
            saveOrder(swapped, 204);
            JsonNode route = call("OPERATOR", get("/api/routes?date=" + today() + "&" + DEPOT), 200);
            assertThat(ids(route)).containsExactlyElementsOf(swapped);
            // Los envíos consolidados de la parada toman la posición de su parada.
            for (JsonNode s : route) {
                for (JsonNode id : s.get("shipmentIds")) {
                    Integer position = jdbc.queryForObject("SELECT route_position FROM shipments WHERE id = ?::uuid",
                            Integer.class, id.asText());
                    assertThat(position).isEqualTo(s.get("position").asInt());
                }
            }
        } finally {
            saveOrder(original, 204);
        }
    }

    @Test
    void unOrdenQueNoTraeLasMismasParadasEsUnConflicto() throws Exception {
        List<String> order = ids(build());
        List<String> missing = order.subList(1, order.size());
        List<String> repeated = new ArrayList<>(order);
        repeated.set(1, order.get(0));

        preview(missing, List.of(), false, 409);
        saveOrder(missing, 409);
        saveOrder(repeated, 400);
        saveOrder(List.of(), 400);
        preview(order, List.of(UUID.randomUUID().toString()), true, 400);
        call("OPERATOR", put("/api/routes/order").content("""
                {"date":"%s","order":%s}""".formatted(today(), json.writeValueAsString(order))), 403);
    }

    @Test
    void elAjusteDeDescargaDeUnLocalSeGuardaYSeQuita() throws Exception {
        Map<String, Object> local = jdbc.queryForMap(
                "SELECT id, unload_extra_minutes, unload_note FROM customers WHERE external_code = 'SEED-C01'");
        String url = "/api/customers/" + local.get("id") + "/unload-adjustment";
        try {
            JsonNode set = call("DISPATCHER", put(url).content("""
                    {"extraMinutes":15,"note":"  sin estacionamiento  "}"""), 200);
            assertThat(set.get("unloadExtraMinutes").asInt()).isEqualTo(15);
            assertThat(set.get("unloadNote").asText()).isEqualTo("sin estacionamiento");

            JsonNode cleared = call("OPERATOR", put(url).content("""
                    {"extraMinutes":0,"note":"no importa"}"""), 200);
            assertThat(cleared.get("unloadExtraMinutes").asInt()).isZero();
            assertThat(cleared.get("unloadNote").isNull()).isTrue();

            call("DISPATCHER", put(url).content("{\"extraMinutes\":121}"), 400);
            call("DISPATCHER", put(url).content("{\"note\":\"x\"}"), 400);
            call("DRIVER", put(url).content("{\"extraMinutes\":5}"), 403);
        } finally {
            jdbc.update("UPDATE customers SET unload_extra_minutes = ?, unload_note = ? WHERE id = ?",
                    local.get("unload_extra_minutes"), local.get("unload_note"), local.get("id"));
        }
    }
}
