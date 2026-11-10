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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-11: GET /api/customers/{id}/time-window, con la semilla cargada.
 *
 * <p>El test central construye la ruta del día y verifica, parada por parada, que la
 * ventana que usa la ruta es la misma que muestra la pantalla del local: hay una sola
 * fuente de verdad (TimeWindowLearningService).
 *
 * OSRM apunta a un puerto cerrado: la ruta usa el fallback Haversine, sin red.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeWindowApiIT {

    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
    /** Bar La Esquina de Ñuñoa: declara 09–12 pero recibe en la noche. */
    private static final String BAR_NUNOA = "5eed0000-0000-4000-8000-000000000004";

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

    private JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        return call("DISPATCHER", req, expectedStatus);
    }

    /**
     * Este IT comparte contexto (y base) con SeedDataIT, que cuenta exactamente los intentos de la
     * semilla: se borra todo lo que crean estos tests (prefijo HU11-).
     */
    @AfterEach
    void borraLoCreadoPorEstosTests() {
        jdbc.update("""
                DELETE FROM delivery_attempts WHERE shipment_id IN
                  (SELECT id FROM shipments WHERE tracking_code LIKE 'HU11-%')""");
        jdbc.update("DELETE FROM shipments WHERE tracking_code LIKE 'HU11-%'");
        jdbc.update("DELETE FROM customers WHERE external_code LIKE 'HU11-%'");
    }

    private JsonNode timeWindow(String customerId) throws Exception {
        return call(get("/api/customers/" + customerId + "/time-window"), 200);
    }

    @Test
    void laRutaYLaPantallaDelLocalUsanLaMismaVentana() throws Exception {
        // La semilla crea los envíos del día con CURRENT_DATE de la base.
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        JsonNode route = call(post("/api/routes/build").content("""
                {"date":"%s","depotLat":-33.45,"depotLng":-70.66}""".formatted(today)), 200);
        // Una parada por local con envíos del día (HU-30).
        assertThat(route.size()).isEqualTo(jdbc.queryForObject(
                "SELECT count(DISTINCT customer_id) FROM shipments WHERE scheduled_date = CURRENT_DATE",
                Integer.class)).isPositive();

        for (JsonNode stop : route) {
            String customerId = jdbc.queryForObject("SELECT customer_id::text FROM shipments WHERE id = ?::uuid",
                    String.class, stop.get("shipmentId").asText());
            JsonNode real = timeWindow(customerId).get("realWindow");
            assertThat(real.get("start").asText()).as(stop.get("customerName").asText())
                    .isEqualTo(stop.get("windowStart").asText().substring(0, 5));
            assertThat(real.get("end").asText()).as(stop.get("customerName").asText())
                    .isEqualTo(stop.get("windowEnd").asText().substring(0, 5));
        }
    }

    @Test
    void explicaLaVentanaRealConVisitasYNoConUnPuntaje() throws Exception {
        JsonNode tw = timeWindow(BAR_NUNOA);

        assertThat(tw.get("customerName").asText()).isEqualTo("Bar La Esquina de Ñuñoa");
        assertThat(tw.get("declaredWindow").get("start").asText()).isEqualTo("09:00");
        assertThat(tw.get("declaredWindow").get("end").asText()).isEqualTo("12:00");

        JsonNode real = tw.get("realWindow");
        assertThat(real.get("start").asText()).isEqualTo("18:00");
        assertThat(real.get("end").asText()).isEqualTo("20:00");
        assertThat(real.get("basis").asText()).isEqualTo("LEARNED");
        int accepted = real.get("acceptedVisits").asInt();
        int visits = real.get("visits").asInt();
        assertThat(visits).isPositive();
        assertThat(accepted).isBetween(1, visits);
        assertThat(tw.get("summary").asText())
                .isEqualTo("Recibió %d de %d visitas entre 18:00 y 20:00".formatted(accepted, visits));
        // Nada de puntajes ni confianza sin explicar.
        assertThat(tw.has("confidence")).isFalse();
        assertThat(real.has("score")).isFalse();

        // El histograma tiene las 24 horas y cuadra con los totales y con la ventana.
        JsonNode histogram = tw.get("histogram");
        assertThat(histogram).hasSize(24);
        int sumAccepted = 0, sumRejected = 0, windowAccepted = 0, windowVisits = 0;
        for (int h = 0; h < 24; h++) {
            JsonNode bucket = histogram.get(h);
            assertThat(bucket.get("hour").asInt()).isEqualTo(h);
            sumAccepted += bucket.get("accepted").asInt();
            sumRejected += bucket.get("rejected").asInt();
            if (h == 18 || h == 19) {
                windowAccepted += bucket.get("accepted").asInt();
                windowVisits += bucket.get("accepted").asInt() + bucket.get("rejected").asInt();
            }
        }
        assertThat(sumAccepted).isEqualTo(tw.get("acceptedAttempts").asInt());
        assertThat(sumRejected).isEqualTo(tw.get("rejectedAttempts").asInt());
        assertThat(sumAccepted + sumRejected).isEqualTo(tw.get("totalAttempts").asInt()).isPositive();
        assertThat(windowAccepted).isEqualTo(accepted);
        assertThat(windowVisits).isEqualTo(visits);
        // Dentro de su modulación (09–12), el bar no recibe: ahí se concentran los rechazos.
        assertThat(histogram.get(11).get("rejected").asInt()).isPositive();
        assertThat(histogram.get(11).get("accepted").asInt()).isZero();
    }

    @Test
    void unLocalSinIntentosMuestraElHorarioPorDefectoQueUsaLaRuta() throws Exception {
        String id = call(post("/api/shipments/customers").content("""
                {"fullName":"Minimarket Nuevo","address":"Av. Nueva 1","commune":"Maipú","latitude":-33.51,"longitude":-70.75,
                 "externalCode":"HU11-%s"}""".formatted(UUID.randomUUID())), 201).get("id").asText();

        JsonNode tw = timeWindow(id);
        assertThat(tw.get("declaredWindow").isNull()).isTrue();
        assertThat(tw.get("realWindow").get("basis").asText()).isEqualTo("DEFAULT");
        assertThat(tw.get("realWindow").get("start").asText()).isEqualTo("09:00");
        assertThat(tw.get("realWindow").get("end").asText()).isEqualTo("18:00");
        assertThat(tw.get("realWindow").get("visits").asInt()).isZero();
        assertThat(tw.get("totalAttempts").asInt()).isZero();
        assertThat(tw.get("summary").asText()).startsWith("Sin intentos de entrega");
        assertThat(tw.get("histogram")).hasSize(24);
    }

    @Test
    void cuentaLasVisitasDeLaFranjaConIntentosRegistradosPorLaApi() throws Exception {
        String customerId = call(post("/api/shipments/customers").content("""
                {"fullName":"Botillería Control","address":"Av. Control 1","commune":"Ñuñoa","latitude":-33.45,"longitude":-70.6,
                 "externalCode":"HU11-%s"}""".formatted(UUID.randomUUID())), 201).get("id").asText();
        String shipmentId = call(post("/api/shipments").content("""
                {"trackingCode":"HU11-%s","customerId":"%s","scheduledDate":"2026-12-01"}"""
                .formatted(UUID.randomUUID(), customerId)), 201).get("id").asText();

        // 3 entregas a las 13:30, 1 rechazo a las 14:30 y 2 a las 10:30 (hora de Santiago).
        attempt(shipmentId, customerId, 13, "ACCEPTED", 3);
        attempt(shipmentId, customerId, 14, "REJECTED_NOT_HOME", 1);
        attempt(shipmentId, customerId, 10, "RESCHEDULED_BY_CUSTOMER", 2);

        JsonNode tw = timeWindow(customerId);
        // Bloque 12–14: 0 + 3 = 3 puntos, mejor que 13–15 (3 − 1,5).
        assertThat(tw.get("realWindow").get("start").asText()).isEqualTo("12:00");
        assertThat(tw.get("realWindow").get("end").asText()).isEqualTo("14:00");
        assertThat(tw.get("summary").asText()).isEqualTo("Recibió 3 de 3 visitas entre 12:00 y 14:00");
        assertThat(tw.get("totalAttempts").asInt()).isEqualTo(6);
        assertThat(tw.get("acceptedAttempts").asInt()).isEqualTo(3);
        assertThat(tw.get("rejectedAttempts").asInt()).isEqualTo(3);
        assertThat(tw.get("hoursToAvoid")).extracting(JsonNode::asInt).containsExactly(10, 14);
        assertThat(tw.get("histogram").get(10).get("rejected").asInt()).isEqualTo(2);
        assertThat(tw.get("histogram").get(13).get("accepted").asInt()).isEqualTo(3);
    }

    private void attempt(String shipmentId, String customerId, int hour, String outcome, int times) throws Exception {
        Instant at = LocalDate.of(2026, 11, 2).atTime(hour, 30).atZone(SANTIAGO).toInstant();
        for (int i = 0; i < times; i++) {
            call(post("/api/shipments/attempts").content("""
                    {"shipmentId":"%s","customerId":"%s","attemptedAt":"%s","outcome":"%s"}"""
                    .formatted(shipmentId, customerId, at, outcome)), 201);
        }
    }

    @Test
    void soloLosRolesInternosVenElPerfilYUnLocalInexistenteEs404() throws Exception {
        call("CLIENT", get("/api/customers/" + BAR_NUNOA + "/time-window"), 403);
        for (String role : new String[]{"ADMIN", "OPERATOR", "DRIVER"}) {
            call(role, get("/api/customers/" + BAR_NUNOA + "/time-window"), 200);
        }
        call(get("/api/customers/" + UUID.randomUUID() + "/time-window"), 404);
        call(get("/api/customers/no-es-uuid/time-window"), 400);
    }

    @Test
    void elOpenApiDocumentaElEndpoint() throws Exception {
        String apiDocs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(apiDocs).contains("/api/customers/{customerId}/time-window", "Modulación vs. ventana real de un local");
    }
}
