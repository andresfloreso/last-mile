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
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-14: POST /api/routes/build con la semilla (26 envíos del día en 24 paradas, HU-30) y hora estimada por parada.
 *
 * OSRM apunta a un puerto cerrado: los tiempos se estiman (timingSource = ESTIMATED), sin red.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoutePlanIT {

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
        return json.readTree(content);
    }

    private LocalDate today() {
        // La semilla crea los envíos del día con CURRENT_DATE de la base.
        return jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
    }

    private JsonNode build(String extraJson) throws Exception {
        return call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.446393,"depotLng":-70.624979%s}""".formatted(today(), extraJson)), 200);
    }

    private static LocalTime t(JsonNode node, String field) {
        return LocalTime.parse(node.get(field).asText());
    }

    /** Minutos desde la medianoche del día programado, contando dayOffset (la ruta puede pasar la medianoche). */
    private static int abs(JsonNode stop, String field) {
        return stop.get("dayOffset").asInt() * 1440 + t(stop, field).toSecondOfDay() / 60;
    }

    @Test
    void cadaParadaTieneUnaHoraEstimadaCoherenteConLaAnterior() throws Exception {
        JsonNode route = build("");
        // Una parada por local con envíos del día (HU-30).
        assertThat(route.size()).isEqualTo(jdbc.queryForObject(
                "SELECT count(DISTINCT customer_id) FROM shipments WHERE scheduled_date = CURRENT_DATE",
                Integer.class)).isPositive();

        int clock = 8 * 60; // salida por defecto, 08:00
        int totalMeters = 0;
        for (int i = 0; i < route.size(); i++) {
            JsonNode stop = route.get(i);
            String name = stop.get("customerName").asText();
            assertThat(stop.get("position").asInt()).isEqualTo(i + 1);
            assertThat(stop.get("timingSource").asText()).isEqualTo("ESTIMATED");

            int arrival = abs(stop, "estimatedArrival");
            int start = arrival + stop.get("waitMinutes").asInt();
            int windowStart = LocalTime.parse(stop.get("windowStart").asText()).toSecondOfDay() / 60;
            int windowEnd = LocalTime.parse(stop.get("windowEnd").asText()).toSecondOfDay() / 60;

            // Llega después de salir de la parada anterior (± 1 min de redondeo).
            assertThat(arrival).as(name).isGreaterThanOrEqualTo(clock - 1);
            // El inicio mostrado es la llegada más la espera (± 1 min de redondeo).
            assertThat(abs(stop, "estimatedArrival") + stop.get("waitMinutes").asInt())
                    .as(name).isCloseTo(start, org.assertj.core.data.Offset.offset(1));
            // El día programado, la entrega nunca empieza antes de que abra la ventana real.
            if (stop.get("dayOffset").asInt() == 0) {
                assertThat(t(stop, "serviceStart").toSecondOfDay() / 60).as(name).isGreaterThanOrEqualTo(windowStart);
            }
            // HU-15: salida = inicio + descarga; descarga = 8 min de base (por defecto) + 1,5 por bulto + ajuste
            // del local. Las horas se muestran truncadas al minuto: ± 1 min.
            int service = (int) Math.round(8 + 1.5 * stop.get("packageCount").asInt() + stop.get("unloadExtraMinutes").asInt());
            assertThat(stop.get("serviceMinutes").asInt()).as(name).isEqualTo(service);
            assertThat(abs(stop, "estimatedDeparture") - abs(stop, "serviceStart")).as(name)
                    .isCloseTo(service, org.assertj.core.data.Offset.offset(1));
            // Fuera de ventana ⇔ empieza cuando la ventana ya cerró (o al día siguiente).
            assertThat(stop.get("outsideWindow").asBoolean()).as(name).isEqualTo(start >= windowEnd);
            // Tramo para la polilínea: en línea recta sin OSRM.
            assertThat(stop.get("path")).as(name).hasSize(2);
            assertThat(stop.get("legDistanceMeters").asInt()).as(name).isGreaterThanOrEqualTo(0);
            totalMeters += stop.get("legDistanceMeters").asInt();
            clock = start + service;
        }
        assertThat(totalMeters).isPositive();
    }

    @Test
    void respetaLaHoraDeSalidaYLaDuracionDeParadaPedidas() throws Exception {
        JsonNode route = build(",\"departureTime\":\"07:30\",\"stopMinutes\":25");
        JsonNode first = route.get(0);
        // La hora se muestra truncada al minuto y travelMinutes va redondeado: ± 1 min.
        LocalTime expected = LocalTime.of(7, 30).plusMinutes(first.get("travelMinutes").asInt());
        assertThat(t(first, "estimatedArrival")).isBetween(expected.minusMinutes(1), expected);
        // HU-15: stopMinutes es la base de la descarga; se suman los bultos y el ajuste del local (± 1 min).
        for (JsonNode stop : route) {
            int service = (int) Math.round(25 + 1.5 * stop.get("packageCount").asInt() + stop.get("unloadExtraMinutes").asInt());
            assertThat(stop.get("serviceMinutes").asInt()).isEqualTo(service);
            assertThat(abs(stop, "estimatedDeparture") - abs(stop, "serviceStart"))
                    .isCloseTo(service, org.assertj.core.data.Offset.offset(1));
        }
    }

    @Test
    void lasParadasPrimeraYUltimaQuedanEnLosExtremosConSuHora() throws Exception {
        String[] ids = jdbc.queryForList("""
                SELECT id::text FROM shipments WHERE scheduled_date = CURRENT_DATE AND source = 'seed'
                ORDER BY tracking_code LIMIT 2""", String.class).toArray(String[]::new);
        call("DISPATCHER", patch("/api/shipments/" + ids[0]).content("{\"orderPreference\":\"LAST\"}"), 200);
        call("DISPATCHER", patch("/api/shipments/" + ids[1]).content("{\"orderPreference\":\"FIRST\"}"), 200);
        try {
            JsonNode route = build("");
            JsonNode first = route.get(0);
            JsonNode last = route.get(route.size() - 1);
            assertThat(first.get("shipmentId").asText()).isEqualTo(ids[1]);
            assertThat(first.get("orderPreference").asText()).isEqualTo("FIRST");
            assertThat(last.get("shipmentId").asText()).isEqualTo(ids[0]);
            assertThat(last.get("orderPreference").asText()).isEqualTo("LAST");
            // La "Última" es también la última en el reloj (contando el paso de la medianoche).
            for (JsonNode stop : route) {
                assertThat(abs(stop, "estimatedArrival")).isLessThanOrEqualTo(abs(last, "estimatedArrival"));
            }
        } finally {
            call("DISPATCHER", patch("/api/shipments/" + ids[0]).content("{\"orderPreference\":\"AUTO\"}"), 200);
            call("DISPATCHER", patch("/api/shipments/" + ids[1]).content("{\"orderPreference\":\"AUTO\"}"), 200);
        }
    }

    /** HU-30: los envíos del mismo local en la fecha forman una sola parada. */
    @Test
    void losEnviosDelMismoLocalFormanUnaSolaParada() throws Exception {
        JsonNode route = build("");
        Map<String, Integer> stopsByCustomer = new java.util.HashMap<>();
        for (JsonNode stop : route) stopsByCustomer.merge(stop.get("customerName").asText(), 1, Integer::sum);
        assertThat(stopsByCustomer.values()).containsOnly(1);

        // El local 1 de la semilla tiene dos envíos hoy.
        UUID local = UUID.fromString("5eed0000-0000-4000-8000-000000000001");
        java.util.List<Map<String, Object>> envios = jdbc.queryForList("""
                SELECT id::text AS id, package_count, route_position, status FROM shipments
                WHERE scheduled_date = CURRENT_DATE AND customer_id = ? ORDER BY tracking_code""", local);
        assertThat(envios).hasSize(2);

        JsonNode stop = null;
        for (JsonNode s : route) {
            if (s.get("shipmentId").asText().equals(envios.get(0).get("id"))) stop = s;
        }
        assertThat(stop).as("parada del local 1").isNotNull();
        assertThat(stop.get("shipmentCount").asInt()).isEqualTo(2);
        assertThat(stop.get("shipmentIds")).extracting(JsonNode::asText)
                .containsExactly((String) envios.get(0).get("id"), (String) envios.get(1).get("id"));
        int packages = envios.stream().mapToInt(e -> ((Number) e.get("package_count")).intValue()).sum();
        assertThat(stop.get("packageCount").asInt()).isEqualTo(packages);
        // Los dos envíos quedan en la misma posición de la ruta y ruteados.
        for (Map<String, Object> e : envios) {
            assertThat(((Number) e.get("route_position")).intValue()).isEqualTo(stop.get("position").asInt());
            assertThat(e.get("status")).isEqualTo("ROUTED");
        }
    }

    @Test
    void validaLaDuracionDeParadaYLosRoles() throws Exception {
        call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.45,"depotLng":-70.66,"stopMinutes":0}""".formatted(today())), 400);
        call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.45,"depotLng":-70.66,"stopMinutes":121}""".formatted(today())), 400);
        call("OPERATOR", post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.45,"depotLng":-70.66}""".formatted(today())), 403);
    }

    @Test
    void elOpenApiDocumentaLaHoraEstimada() throws Exception {
        String apiDocs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(apiDocs).contains("Arma la ruta del día con hora estimada por parada",
                "estimatedArrival", "outsideWindow", "departureTime", "stopMinutes",
                "shipmentIds", "shipmentCount");
    }
}
