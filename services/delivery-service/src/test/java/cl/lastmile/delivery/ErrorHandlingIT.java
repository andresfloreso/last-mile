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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #12: delivery-service responde 400/409 específicos y nunca expone detalles
 * internos (SQL, constraints, clases Java ni mensajes de librerías).
 */
@SpringBootTest(properties = "geocoding.nominatim-url=http://localhost:1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ErrorHandlingIT {

    /** Fragmentos que nunca deben llegar al cliente. */
    private static final String[] LEAKS = {
            "insert", "select", "constraint", "uk_", "sql", "jdbc", "hibernate",
            "exception", "cl.lastmile", "java.", "jackson", "json parse"
    };

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${jwt.secret}") String jwtSecret;

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req) {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claims(Map.of("role", "DISPATCHER", "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        return req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        String content = mvc.perform(auth(req))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(content);
    }

    private static void assertNoLeaks(JsonNode body) {
        String message = body.get("message").asText().toLowerCase();
        for (String leak : LEAKS) {
            assertThat(message).as("el mensaje no debe contener '%s'", leak).doesNotContain(leak);
        }
    }

    private String createCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return call(post("/api/shipments/customers").content("""
                {"fullName":"Botillería El Roble","address":"Av. Matta 123","commune":"Santiago",
                 "latitude":-33.46,"longitude":-70.64,"externalCode":"ERR-%s"}""".formatted(suffix)), 201)
                .get("id").asText();
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        JsonNode body = call(post("/api/shipments").content("{\"trackingCode\": "), 400);
        assertThat(body.get("message").asText()).contains("JSON válido");
        assertNoLeaks(body);
    }

    @Test
    void codificacionInvalidaResponde400() throws Exception {
        byte[] latin1 = "{\"fullName\":\"Muñoz\",\"address\":\"x\"}".getBytes(StandardCharsets.ISO_8859_1);
        JsonNode body = call(post("/api/shipments/customers").content(latin1), 400);
        assertNoLeaks(body);
    }

    @Test
    void fechaConFormatoInvalidoEnElCuerpoResponde400() throws Exception {
        String customerId = createCustomer();
        JsonNode body = call(post("/api/shipments").content("""
                {"trackingCode":"ERR-FECHA","customerId":"%s","scheduledDate":"20-11-2026"}""".formatted(customerId)), 400);
        assertNoLeaks(body);
    }

    @Test
    void trackingCodeDuplicadoResponde409SinDetallesInternos() throws Exception {
        String customerId = createCustomer();
        String tracking = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
        String shipment = """
                {"trackingCode":"%s","customerId":"%s","scheduledDate":"2026-11-20"}""".formatted(tracking, customerId);

        call(post("/api/shipments").content(shipment), 201);
        JsonNode body = call(post("/api/shipments").content(shipment), 409);

        assertThat(body.get("message").asText()).isEqualTo("Ya existe un envío con el trackingCode " + tracking);
        assertNoLeaks(body);
    }

    @Test
    void duplicarConUnTrackingCodeExistenteResponde409() throws Exception {
        String customerId = createCustomer();
        String tracking = "DUP2-" + UUID.randomUUID().toString().substring(0, 8);
        String id = call(post("/api/shipments").content("""
                {"trackingCode":"%s","customerId":"%s","scheduledDate":"2026-11-20"}""".formatted(tracking, customerId)), 201)
                .get("id").asText();

        JsonNode body = call(post("/api/shipments/" + id + "/duplicate").param("newTrackingCode", tracking), 409);
        assertNoLeaks(body);
    }

    @Test
    void idQueNoEsUuidResponde400() throws Exception {
        JsonNode body = call(post("/api/shipments").content("""
                {"trackingCode":"ERR-UUID","customerId":"no-es-uuid","scheduledDate":"2026-11-20"}"""), 400);
        assertThat(body.get("message").asText()).isEqualTo("customerId no es un identificador válido: no-es-uuid");

        JsonNode patchBody = call(patch("/api/shipments/123").content("{}"), 400);
        assertThat(patchBody.get("message").asText()).startsWith("shipmentId no es un identificador válido");
    }

    @Test
    void enumInvalidoResponde400ConLosValoresPermitidos() throws Exception {
        String customerId = createCustomer();
        JsonNode body = call(post("/api/shipments").content("""
                {"trackingCode":"ERR-ENUM","customerId":"%s","scheduledDate":"2026-11-20","stopType":"VOLAR"}"""
                .formatted(customerId)), 400);
        assertThat(body.get("message").asText())
                .isEqualTo("stopType inválido: VOLAR. Valores permitidos: DELIVERY, PICKUP");
        assertNoLeaks(body);

        JsonNode attempt = call(post("/api/shipments/attempts").content("""
                {"shipmentId":"%s","customerId":"%s","outcome":"QUIZAS"}""".formatted(UUID.randomUUID(), customerId)), 404);
        assertNoLeaks(attempt);
    }

    @Test
    void parametroFaltanteOInvalidoResponde400() throws Exception {
        assertThat(call(post("/api/shipments/" + UUID.randomUUID() + "/duplicate"), 400).get("message").asText())
                .isEqualTo("Falta el parámetro obligatorio 'newTrackingCode'");
        assertThat(call(get("/api/shipments").param("date", "mañana"), 400).get("message").asText())
                .isEqualTo("El parámetro 'date' tiene un formato inválido");
    }

    @Test
    void rutaInexistenteResponde404() throws Exception {
        JsonNode body = call(get("/api/no-existe"), 404);
        assertNoLeaks(body);
    }
}
