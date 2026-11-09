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
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #11: los endpoints de envíos devuelven DTOs. Antes, GET /api/shipments
 * respondía 500 al serializar el proxy LAZY de Hibernate en Shipment.customer.
 *
 * spring.jpa.open-in-view=false reproduce el peor caso: el mapeo debe ocurrir
 * dentro de la transacción del servicio, no durante la serialización.
 */
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentApiIT {

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

    private JsonNode body(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        String content = mvc.perform(auth(req))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(content);
    }

    @Test
    void cicloCompletoDeEnviosDevuelveDtosConElClienteEmbebido() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 20);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        JsonNode customer = body(post("/api/shipments/customers").content("""
                {"fullName":"Andrea Muñoz","address":"Plaza de Armas s/n","commune":"Santiago",
                 "latitude":-33.4378,"longitude":-70.6504,"externalCode":"IT-%s"}""".formatted(suffix)), 201);
        String customerId = customer.get("id").asText();

        JsonNode shipment = body(post("/api/shipments").content("""
                {"trackingCode":"IT-%s","customerId":"%s","scheduledDate":"%s","packageCount":2}"""
                .formatted(suffix, customerId, date)), 201);
        String shipmentId = shipment.get("id").asText();

        // El caso que fallaba con 500: listar envíos con el cliente LAZY.
        mvc.perform(auth(get("/api/shipments").param("date", date.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].trackingCode").value("IT-" + suffix))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].packageCount").value(2))
                .andExpect(jsonPath("$.content[0].customer.id").value(customerId))
                .andExpect(jsonPath("$.content[0].customer.fullName").value("Andrea Muñoz"))
                .andExpect(jsonPath("$.content[0].customer.latitude").value(-33.4378))
                .andExpect(jsonPath("$.content[0].hibernateLazyInitializer").doesNotExist());

        mvc.perform(auth(patch("/api/shipments/" + shipmentId)).content("""
                        {"label":"Urgente","orderPreference":"FIRST"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Urgente"))
                .andExpect(jsonPath("$.customer.fullName").value("Andrea Muñoz"));

        mvc.perform(auth(post("/api/shipments/" + shipmentId + "/duplicate").param("newTrackingCode", "IT-DUP-" + suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingCode").value("IT-DUP-" + suffix))
                .andExpect(jsonPath("$.customer.id").value(customerId));

        mvc.perform(auth(post("/api/shipments/attempts")).content("""
                        {"shipmentId":"%s","customerId":"%s","outcome":"ACCEPTED"}""".formatted(shipmentId, customerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));
    }

    @Test
    void reutilizaElClientePorCodigoExterno() throws Exception {
        String code = "IT-EXT-" + UUID.randomUUID().toString().substring(0, 8);
        String req = """
                {"fullName":"Cliente","address":"Av. Siempre Viva 123","latitude":-33.4,"longitude":-70.6,"externalCode":"%s"}"""
                .formatted(code);

        String first = body(post("/api/shipments/customers").content(req), 201).get("id").asText();
        String second = body(post("/api/shipments/customers").content(req), 201).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }
}
