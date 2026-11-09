package cl.lastmile.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-25: tipo de local y modulación declarada (API + constraints de la migración V2). */
@SpringBootTest(properties = "geocoding.nominatim-url=http://localhost:1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerApiIT {

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

    private JsonNode call(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        return call("DISPATCHER", req, expectedStatus);
    }

    private JsonNode call(String role, MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        String content = mvc.perform(as(role, req))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(content);
    }

    private JsonNode createLocal(String externalCode, String extraJson) throws Exception {
        return call(post("/api/shipments/customers").content("""
                {"fullName":"Botillería El Roble","address":"Av. Providencia 2124","commune":"Providencia",
                 "latitude":-33.42,"longitude":-70.61,"externalCode":"%s"%s}""".formatted(externalCode, extraJson)), 201);
    }

    private static String code() {
        return "HU25-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void creaUnLocalConTipoYModulacion() throws Exception {
        JsonNode local = createLocal(code(), """
                ,"businessType":"botilleria","declaredWindowStart":"09:00","declaredWindowEnd":"12:30\"""");

        assertThat(local.get("businessType").asText()).isEqualTo("BOTILLERIA");
        assertThat(local.get("declaredWindow").get("start").asText()).isEqualTo("09:00");
        assertThat(local.get("declaredWindow").get("end").asText()).isEqualTo("12:30");
        assertThat(local.get("declaredWindow").get("start2").isNull()).isTrue();

        JsonNode detail = call(get("/api/customers/" + local.get("id").asText()), 200);
        assertThat(detail.get("declaredWindow").get("end").asText()).isEqualTo("12:30");
    }

    @Test
    void unLocalSinModulacionDevuelveDeclaredWindowNull() throws Exception {
        JsonNode local = createLocal(code(), "");
        assertThat(local.get("businessType").isNull()).isTrue();
        assertThat(local.get("declaredWindow").isNull()).isTrue();
    }

    @Test
    void reimportarActualizaLaModulacionSinDuplicarElLocal() throws Exception {
        String externalCode = code();
        String id = createLocal(externalCode, """
                ,"declaredWindowStart":"09:00","declaredWindowEnd":"12:00\"""").get("id").asText();

        JsonNode again = createLocal(externalCode, """
                ,"businessType":"BAR","declaredWindowStart":"19:00","declaredWindowEnd":"23:00\"""");
        assertThat(again.get("id").asText()).isEqualTo(id);
        assertThat(again.get("businessType").asText()).isEqualTo("BAR");
        assertThat(again.get("declaredWindow").get("start").asText()).isEqualTo("19:00");

        // Una fila sin modulación conserva la que ya tenía el local.
        JsonNode withoutWindow = createLocal(externalCode, "");
        assertThat(withoutWindow.get("declaredWindow").get("start").asText()).isEqualTo("19:00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE external_code = ?",
                Integer.class, externalCode)).isEqualTo(1);
    }

    @Test
    void editaLaModulacionConDosTramosYLaElimina() throws Exception {
        String id = createLocal(code(), "").get("id").asText();

        JsonNode updated = call(put("/api/customers/" + id + "/declared-window").content("""
                {"start":"09:00","end":"12:00","start2":"16:00","end2":"18:00"}"""), 200);
        assertThat(updated.get("declaredWindow").get("start2").asText()).isEqualTo("16:00");
        assertThat(updated.get("declaredWindow").get("end2").asText()).isEqualTo("18:00");

        JsonNode cleared = call(put("/api/customers/" + id + "/declared-window").content("""
                {"start":null,"end":null}"""), 200);
        assertThat(cleared.get("declaredWindow").isNull()).isTrue();
    }

    @Test
    void rechazaModulacionesInvalidasCon400() throws Exception {
        String id = createLocal(code(), "").get("id").asText();
        String url = "/api/customers/" + id + "/declared-window";

        assertThat(call(put(url).content("{\"start\":\"09:00\"}"), 400).get("message").asText())
                .isEqualTo("La modulación necesita hora de inicio y de término");
        assertThat(call(put(url).content("{\"start\":\"12:00\",\"end\":\"09:00\"}"), 400).get("message").asText())
                .startsWith("Modulación inválida");
        assertThat(call(put(url).content("""
                {"start":"09:00","end":"12:00","start2":"11:00","end2":"13:00"}"""), 400).get("message").asText())
                .startsWith("El segundo tramo debe empezar después del término del primero");
        assertThat(call(put(url).content("{\"start\":\"25:00\",\"end\":\"26:00\"}"), 400).get("message").asText())
                .contains("formato incorrecto");

        assertThat(call(post("/api/shipments/customers").content("""
                {"fullName":"X","address":"Y","businessType":"KIOSCO"}"""), 400).get("message").asText())
                .isEqualTo("businessType inválido: KIOSCO. Valores permitidos: BOTILLERIA, MINIMARKET, BAR, RESTAURANT, OTRO");
    }

    @Test
    void soloAdminDespachadorUOperadorEditanLaModulacion() throws Exception {
        String id = createLocal(code(), "").get("id").asText();
        call("DRIVER", put("/api/customers/" + id + "/declared-window").content("{\"start\":\"09:00\",\"end\":\"12:00\"}"), 403);
        call("OPERATOR", put("/api/customers/" + id + "/declared-window").content("{\"start\":\"09:00\",\"end\":\"12:00\"}"), 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DISPATCHER", "OPERATOR", "DRIVER"})
    void losRolesInternosVenElDetalleDelLocal(String role) throws Exception {
        String id = createLocal(code(), "").get("id").asText();
        assertThat(call(role, get("/api/customers/" + id), 200).get("id").asText()).isEqualTo(id);
    }

    /** #38: CLIENT es el rol del registro público; no debe leer el detalle de los locales. */
    @Test
    void unClienteNoVeElDetalleDelLocal() throws Exception {
        String id = createLocal(code(), "").get("id").asText();
        call("CLIENT", get("/api/customers/" + id), 403);
        // Tampoco distingue un local inexistente de uno existente.
        call("CLIENT", get("/api/customers/" + UUID.randomUUID()), 403);
    }

    @Test
    void localInexistenteResponde404() throws Exception {
        call(get("/api/customers/" + UUID.randomUUID()), 404);
    }

    @Test
    void laBaseRechazaModulacionesInconsistentesAunqueNoPasenPorLaApi() {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customers (id, full_name, address, declared_window_start, declared_window_end)
                VALUES (?::uuid, 'X', 'Y', '12:00', '09:00')""", id))
                .hasMessageContaining("ck_customers_declared_window");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customers (id, full_name, address, business_type) VALUES (?::uuid, 'X', 'Y', 'KIOSCO')""", id))
                .hasMessageContaining("ck_customers_business_type");
    }

    @Test
    void elOpenApiDocumentaLosEndpointsDeLocales() throws Exception {
        String apiDocs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(apiDocs).contains("/api/customers/{customerId}/declared-window", "Reemplaza la modulación de un local");
    }
}
