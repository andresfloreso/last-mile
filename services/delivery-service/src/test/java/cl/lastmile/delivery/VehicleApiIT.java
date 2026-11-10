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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-29: API de vehículos y peso/volumen por envío, sobre la semilla.
 *
 * Comparte el contexto (y la base) con SeedDataIT y RoutePlanIT: borra lo que crea.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=seed",
        "SEED_LOCATION=../../infra/seed/delivery",
        "routing.osrm-url=http://localhost:1",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VehicleApiIT {

    private static final String SEED_LOCAL = "5eed0000-0000-4000-8000-000000000001";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Value("${jwt.secret}") String jwtSecret;

    @AfterEach
    void borraLoCreadoPorEstosTests() {
        jdbc.update("DELETE FROM vehicles WHERE plate LIKE 'TEST%'");
        jdbc.update("DELETE FROM shipments WHERE tracking_code LIKE 'HU29-%'");
    }

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

    private static String vehicle(String plate, double kg, double m3, String start, String end) {
        return """
                {"plate":"%s","name":"Camión de prueba","capacityKg":%s,"capacityM3":%s,
                 "shiftStart":"%s","shiftEnd":"%s"}""".formatted(plate, kg, m3, start, end);
    }

    private static List<String> plates(JsonNode list) {
        List<String> plates = new ArrayList<>();
        list.forEach(v -> plates.add(v.get("plate").asText()));
        return plates;
    }

    @Test
    void laSemillaTraeCamionesActivosYCualquierRolInternoDeOficinaLosVe() throws Exception {
        JsonNode list = call("OPERATOR", get("/api/vehicles"), 200);
        assertThat(list.size()).isPositive();
        for (JsonNode v : list) {
            assertThat(v.get("active").asBoolean()).isTrue();
            assertThat(v.get("capacityKg").asDouble()).isPositive();
            assertThat(v.get("capacityM3").asDouble()).isPositive();
            assertThat(v.get("shiftStart").asText()).matches("\\d{2}:\\d{2}");
        }
        assertThat(plates(list)).isSorted();
        call("DRIVER", get("/api/vehicles"), 403);
    }

    @Test
    void registraUnCamionYNormalizaLaPatente() throws Exception {
        JsonNode created = call("DISPATCHER", post("/api/vehicles").content(vehicle("test 01", 3500, 18, "08:00", "20:00")), 201);
        assertThat(created.get("plate").asText()).isEqualTo("TEST01");
        assertThat(created.get("capacityKg").asDouble()).isEqualTo(3500);
        assertThat(created.get("capacityM3").asDouble()).isEqualTo(18);
        assertThat(created.get("shiftStart").asText()).isEqualTo("08:00");
        assertThat(created.get("shiftEnd").asText()).isEqualTo("20:00");
        assertThat(created.get("active").asBoolean()).isTrue();

        JsonNode fetched = call("ADMIN", get("/api/vehicles/" + created.get("id").asText()), 200);
        assertThat(fetched).isEqualTo(created);

        // La patente no se repite, aunque venga en minúsculas.
        call("ADMIN", post("/api/vehicles").content(vehicle("Test01", 1000, 5, "08:00", "18:00")), 409);
    }

    @Test
    void validaCapacidadJornadaPatenteYRoles() throws Exception {
        call("ADMIN", post("/api/vehicles").content(vehicle("TEST02", 0, 18, "08:00", "20:00")), 400);
        call("ADMIN", post("/api/vehicles").content(vehicle("TEST02", 3500, -1, "08:00", "20:00")), 400);
        call("ADMIN", post("/api/vehicles").content(vehicle("TEST02", 3500, 18, "20:00", "08:00")), 400);
        call("ADMIN", post("/api/vehicles").content(vehicle("TEST/02", 3500, 18, "08:00", "20:00")), 400);
        call("ADMIN", post("/api/vehicles").content("""
                {"plate":"TEST02","capacityKg":3500,"capacityM3":18,"shiftStart":"08:00"}"""), 400);
        call("OPERATOR", post("/api/vehicles").content(vehicle("TEST02", 3500, 18, "08:00", "20:00")), 403);
        call("ADMIN", get("/api/vehicles/no-es-uuid"), 400);
        call("ADMIN", get("/api/vehicles/" + UUID.randomUUID()), 404);
    }

    @Test
    void editarloOdarloDeBajaNoLoBorra() throws Exception {
        JsonNode a = call("ADMIN", post("/api/vehicles").content(vehicle("TEST03", 1200, 8, "08:00", "18:00")), 201);
        call("ADMIN", post("/api/vehicles").content(vehicle("TEST04", 1200, 8, "08:00", "18:00")), 201);
        String id = a.get("id").asText();

        JsonNode updated = call("DISPATCHER", put("/api/vehicles/" + id).content("""
                {"plate":"TEST03","name":"Furgón","capacityKg":1500,"capacityM3":9,
                 "shiftStart":"07:30","shiftEnd":"17:30","active":false}"""), 200);
        assertThat(updated.get("capacityKg").asDouble()).isEqualTo(1500);
        assertThat(updated.get("shiftStart").asText()).isEqualTo("07:30");
        assertThat(updated.get("active").asBoolean()).isFalse();

        assertThat(plates(call("ADMIN", get("/api/vehicles"), 200))).doesNotContain("TEST03").contains("TEST04");
        assertThat(plates(call("ADMIN", get("/api/vehicles?includeInactive=true"), 200))).contains("TEST03", "TEST04");

        // No puede tomar la patente de otro.
        call("ADMIN", put("/api/vehicles/" + id).content(vehicle("TEST04", 1500, 9, "07:30", "17:30")), 409);
        call("ADMIN", put("/api/vehicles/" + UUID.randomUUID()).content(vehicle("TEST05", 1500, 9, "07:30", "17:30")), 404);
        call("OPERATOR", put("/api/vehicles/" + id).content(vehicle("TEST03", 1500, 9, "07:30", "17:30")), 403);
    }

    @Test
    void elEnvioTienePesoYVolumenConValorPorDefecto() throws Exception {
        JsonNode withData = call("DISPATCHER", post("/api/shipments").content("""
                {"trackingCode":"HU29-1","customerId":"%s","scheduledDate":"2030-01-02",
                 "weightKg":24.5,"volumeM3":0.08}""".formatted(SEED_LOCAL)), 201);
        assertThat(withData.get("weightKg").asDouble()).isEqualTo(24.5);
        assertThat(withData.get("volumeM3").asDouble()).isEqualTo(0.08);

        // Sin peso ni volumen: el valor por defecto documentado (10 kg y 0,05 m³).
        JsonNode withoutData = call("DISPATCHER", post("/api/shipments").content("""
                {"trackingCode":"HU29-2","customerId":"%s","scheduledDate":"2030-01-02"}""".formatted(SEED_LOCAL)), 201);
        assertThat(withoutData.get("weightKg").asDouble()).isEqualTo(10.0);
        assertThat(withoutData.get("volumeM3").asDouble()).isEqualTo(0.05);

        call("DISPATCHER", post("/api/shipments").content("""
                {"trackingCode":"HU29-3","customerId":"%s","scheduledDate":"2030-01-02","weightKg":0}""".formatted(SEED_LOCAL)), 400);

        String id = withoutData.get("id").asText();
        JsonNode edited = call("DISPATCHER", patch("/api/shipments/" + id).content("{\"weightKg\":12,\"volumeM3\":0.1}"), 200);
        assertThat(edited.get("weightKg").asDouble()).isEqualTo(12.0);
        assertThat(edited.get("volumeM3").asDouble()).isEqualTo(0.1);
        call("DISPATCHER", patch("/api/shipments/" + id).content("{\"volumeM3\":-0.1}"), 400);
    }

    @Test
    void laParadaConsolidadaSumaPesoYVolumen() throws Exception {
        // Dos envíos del mismo local en una fecha sin otros envíos: una parada con la suma.
        for (String body : List.of(
                "{\"trackingCode\":\"HU29-A\",\"customerId\":\"%s\",\"scheduledDate\":\"2030-01-03\",\"weightKg\":20,\"volumeM3\":0.1}",
                "{\"trackingCode\":\"HU29-B\",\"customerId\":\"%s\",\"scheduledDate\":\"2030-01-03\",\"weightKg\":5.5,\"volumeM3\":0.02}")) {
            call("DISPATCHER", post("/api/shipments").content(body.formatted(SEED_LOCAL)), 201);
        }
        JsonNode route = call("DISPATCHER", post("/api/routes/build").content("""
                {"date":"2030-01-03","depotLat":-33.446393,"depotLng":-70.624979}"""), 200);
        assertThat(route).hasSize(1);
        assertThat(route.get(0).get("shipmentCount").asInt()).isEqualTo(2);
        assertThat(route.get(0).get("weightKg").asDouble()).isEqualTo(25.5);
        assertThat(route.get(0).get("volumeM3").asDouble()).isCloseTo(0.12, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void elOpenApiDocumentaVehiculosYPesoYVolumen() throws Exception {
        String apiDocs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(apiDocs).contains("/api/vehicles", "/api/vehicles/{vehicleId}", "Registra un vehículo",
                "capacityKg", "capacityM3", "shiftStart", "weightKg", "volumeM3");
    }
}
