package cl.lastmile.delivery;

import cl.lastmile.delivery.geo.FakeNominatim;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-09: geocodificación al crear locales sin coordenadas (importación), con
 * un Nominatim falso en un puerto local: sin red.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GeocodingIT {

    static final FakeNominatim NOMINATIM = new FakeNominatim();

    @DynamicPropertySource
    static void nominatim(DynamicPropertyRegistry registry) {
        registry.add("geocoding.nominatim-url", NOMINATIM::url);
        registry.add("geocoding.min-interval", () -> "0s"); // el límite de 1 req/s se prueba en GeocodingServiceTest
    }

    @AfterAll
    static void stopNominatim() {
        NOMINATIM.close();
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Value("${jwt.secret}") String jwtSecret;

    private JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claims(Map.of("role", "DISPATCHER", "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        String content = mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(content);
    }

    /** Palabra única por test: el contexto (y la caché) se comparten entre tests. */
    private static String unique() {
        return "calle" + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode createLocal(String address, String coords) throws Exception {
        return call(post("/api/shipments/customers").content("""
                {"fullName":"Minimarket Don Lucho","address":"%s","commune":"Ñuñoa",
                 "externalCode":"HU09-%s"%s}""".formatted(address, UUID.randomUUID(), coords)), 201);
    }

    @Test
    void unaDireccionSinCoordenadasSeGeocodificaConNominatim() throws Exception {
        String street = unique();
        NOMINATIM.found(street, -33.4569, -70.5978, 30);

        JsonNode local = createLocal(street + " 1234", "");

        assertThat(local.get("latitude").asDouble()).isEqualTo(-33.4569);
        assertThat(local.get("longitude").asDouble()).isEqualTo(-70.5978);
        assertThat(local.get("geocodeStatus").asText()).isEqualTo("GEOCODED");
        assertThat(NOMINATIM.requestsContaining(street + " 1234, Ñuñoa, Chile")).isEqualTo(1);
    }

    @Test
    void unResultadoAproximadoQuedaPorRevisarYSeVeAlListarLosEnvios() throws Exception {
        String street = unique();
        NOMINATIM.found(street, -33.45, -70.60, 26); // solo la calle, sin número

        JsonNode local = createLocal(street + " 50", "");
        assertThat(local.get("geocodeStatus").asText()).isEqualTo("REVIEW");
        assertThat(local.get("latitude").asDouble()).isEqualTo(-33.45);

        String tracking = "HU09-" + UUID.randomUUID();
        call(post("/api/shipments").content("""
                {"trackingCode":"%s","customerId":"%s","scheduledDate":"2026-12-02","source":"import:test.xlsx"}"""
                .formatted(tracking, local.get("id").asText())), 201);

        JsonNode shipments = call(get("/api/shipments").param("date", "2026-12-02"), 200);
        JsonNode shipment = null;
        for (JsonNode s : shipments) {
            if (s.get("trackingCode").asText().equals(tracking)) shipment = s;
        }
        assertThat(shipment).isNotNull();
        assertThat(shipment.get("customer").get("geocodeStatus").asText()).isEqualTo("REVIEW");
    }

    @Test
    void unaDireccionNoEncontradaQuedaSinCoordenadasYPorRevisar() throws Exception {
        JsonNode local = createLocal(unique() + " 999", "");

        assertThat(local.get("latitude").isNull()).isTrue();
        assertThat(local.get("longitude").isNull()).isTrue();
        assertThat(local.get("geocodeStatus").asText()).isEqualTo("REVIEW");
    }

    @Test
    void conCoordenadasEnLaFilaNoSeConsultaNominatim() throws Exception {
        String street = unique();

        JsonNode local = createLocal(street + " 10", ",\"latitude\":-33.44,\"longitude\":-70.65");

        assertThat(local.get("geocodeStatus").asText()).isEqualTo("PROVIDED");
        assertThat(local.get("latitude").asDouble()).isEqualTo(-33.44);
        assertThat(NOMINATIM.requestsContaining(street)).isZero();
    }

    @Test
    void dosLocalesEnLaMismaDireccionConsultanNominatimUnaSolaVez() throws Exception {
        String street = unique();
        NOMINATIM.found(street, -33.43, -70.62, 30);

        createLocal(street + " 77", "");
        createLocal(street + " 77", "");

        assertThat(NOMINATIM.requestsContaining(street)).isEqualTo(1);
    }

    @Test
    void fijarLaUbicacionQuitaLaMarcaRevisar() throws Exception {
        JsonNode local = createLocal(unique() + " 5", "");
        assertThat(local.get("geocodeStatus").asText()).isEqualTo("REVIEW");
        String id = local.get("id").asText();

        call(put("/api/customers/{id}/location", id).content("{\"latitude\":-91,\"longitude\":-70.6}"), 400);
        call(put("/api/customers/{id}/location", id).content("{\"latitude\":-33.4}"), 400);

        JsonNode fixed = call(put("/api/customers/{id}/location", id)
                .content("{\"latitude\":-33.4501,\"longitude\":-70.6012}"), 200);
        assertThat(fixed.get("geocodeStatus").asText()).isEqualTo("PROVIDED");
        assertThat(fixed.get("latitude").asDouble()).isEqualTo(-33.4501);
    }

    @Test
    void reimportarConCoordenadasCorrigeUnLocalPorRevisar() throws Exception {
        String externalCode = "HU09-" + UUID.randomUUID();
        String body = """
                {"fullName":"Bar La Esquina","address":"%s 8","externalCode":"%s"%s}""";
        String address = unique();

        JsonNode first = call(post("/api/shipments/customers").content(body.formatted(address, externalCode, "")), 201);
        assertThat(first.get("geocodeStatus").asText()).isEqualTo("REVIEW");

        JsonNode again = call(post("/api/shipments/customers")
                .content(body.formatted(address, externalCode, ",\"latitude\":-33.41,\"longitude\":-70.58")), 201);
        assertThat(again.get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(again.get("geocodeStatus").asText()).isEqualTo("PROVIDED");
        assertThat(again.get("latitude").asDouble()).isEqualTo(-33.41);
    }

    @Test
    void laBaseRechazaUnEstadoDeGeocodificacionInvalido() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customers (id, full_name, address, geocode_status)
                VALUES (gen_random_uuid(), 'x', 'y', 'MAYBE')"""))
                .hasMessageContaining("ck_customers_geocode_status");
    }
}
