package cl.lastmile.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-10: GET /api/shipments paginado con filtros por fecha, estado, comuna y búsqueda.
 *
 * <p>Cada test crea sus datos con un sufijo propio y en fechas que nadie más usa,
 * para no depender de lo que dejen otros IT en la misma base.
 * open-in-view=false obliga a que el local se cargue dentro de la transacción (#11).
 */
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "geocoding.nominatim-url=http://localhost:1"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentListIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Value("${jwt.secret}") String jwtSecret;

    /** Días exclusivos de esta clase: un año lejano que no usa ningún otro IT, 10 días por test. */
    private static final AtomicInteger NEXT_DAY = new AtomicInteger();
    private LocalDate day;
    private String suffix;

    @BeforeEach
    void setUp() {
        day = LocalDate.of(2041, 1, 1).plusDays(10L * NEXT_DAY.getAndIncrement());
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

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
        String content = mvc.perform(auth(req)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(content);
    }

    private String local(String name, String address, String commune) throws Exception {
        return call(post("/api/shipments/customers").content("""
                {"fullName":"%s","address":"%s","commune":"%s","latitude":-33.43,"longitude":-70.62,
                 "externalCode":"EXT-%s-%s"}""".formatted(name, address, commune, name.hashCode(), suffix)), 201)
                .get("id").asText();
    }

    private String shipment(String tracking, String customerId, LocalDate date) throws Exception {
        return call(post("/api/shipments").content("""
                {"trackingCode":"%s","customerId":"%s","scheduledDate":"%s"}"""
                .formatted(tracking, customerId, date)), 201).get("id").asText();
    }

    /** GET /api/shipments con pares nombre=valor ("date=2040-01-01&q=roble"); los valores van sin codificar. */
    private JsonNode list(String query) throws Exception {
        return call(withParams(query), 200);
    }

    private static MockHttpServletRequestBuilder withParams(String query) {
        MockHttpServletRequestBuilder req = get("/api/shipments");
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            req.param(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return req;
    }

    private static List<String> trackings(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("content").forEach(s -> out.add(s.get("trackingCode").asText()));
        return out;
    }

    @Test
    void paginaYOrdenaPorPosicionEnLaRutaYLuegoPorTracking() throws Exception {
        String c = local("Minimarket Paginado " + suffix, "Av. Matta 100", "Santiago");
        for (String t : new String[]{"E", "C", "A", "D", "B"}) shipment(t + "-" + suffix, c, day);
        jdbc.update("UPDATE shipments SET route_position = 1 WHERE tracking_code = ?", "D-" + suffix);
        jdbc.update("UPDATE shipments SET route_position = 2 WHERE tracking_code = ?", "B-" + suffix);

        JsonNode first = list("date=" + day + "&size=2");
        assertThat(first.get("totalElements").asLong()).isEqualTo(5);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        assertThat(first.get("page").asInt()).isZero();
        assertThat(first.get("size").asInt()).isEqualTo(2);
        // Primero los que tienen posición en la ruta; los sin posición van al final, por tracking.
        assertThat(trackings(first)).containsExactly("D-" + suffix, "B-" + suffix);
        assertThat(trackings(list("date=" + day + "&size=2&page=1"))).containsExactly("A-" + suffix, "C-" + suffix);
        assertThat(trackings(list("date=" + day + "&size=2&page=2"))).containsExactly("E-" + suffix);
        assertThat(list("date=" + day + "&size=2&page=3").get("content")).isEmpty();

        // Cada envío trae su local embebido (sin proxies de Hibernate).
        JsonNode customer = first.get("content").get(0).get("customer");
        assertThat(customer.get("fullName").asText()).isEqualTo("Minimarket Paginado " + suffix);
        assertThat(customer.has("hibernateLazyInitializer")).isFalse();
    }

    @Test
    void filtraPorUnDiaOPorRangoDeFechasYOrdenaLaFechaMasRecientePrimero() throws Exception {
        String c = local("Bar Fechas " + suffix, "Irarrázaval 2000", "Ñuñoa");
        shipment("F0-" + suffix, c, day);
        shipment("F1-" + suffix, c, day.plusDays(1));
        shipment("F2-" + suffix, c, day.plusDays(2));
        String scope = "&q=" + suffix;

        assertThat(trackings(list("date=" + day.plusDays(1) + scope))).containsExactly("F1-" + suffix);
        assertThat(trackings(list("from=" + day + "&to=" + day.plusDays(1) + scope)))
                .containsExactly("F1-" + suffix, "F0-" + suffix);
        assertThat(trackings(list("from=" + day.plusDays(1) + scope)))
                .containsExactly("F2-" + suffix, "F1-" + suffix);
        assertThat(trackings(list("to=" + day + scope))).containsExactly("F0-" + suffix);
    }

    @Test
    void filtraPorUnoOVariosEstados() throws Exception {
        String c = local("Restaurant Estados " + suffix, "Av. Italia 900", "Providencia");
        shipment("S1-" + suffix, c, day);
        shipment("S2-" + suffix, c, day);
        shipment("S3-" + suffix, c, day);
        jdbc.update("UPDATE shipments SET status = 'DELIVERED' WHERE tracking_code = ?", "S2-" + suffix);
        jdbc.update("UPDATE shipments SET status = 'REJECTED' WHERE tracking_code = ?", "S3-" + suffix);

        assertThat(trackings(list("date=" + day + "&status=PENDING"))).containsExactly("S1-" + suffix);
        assertThat(trackings(list("date=" + day + "&status=delivered&status=REJECTED")))
                .containsExactly("S2-" + suffix, "S3-" + suffix);
        assertThat(trackings(list("date=" + day + "&status=DELIVERED,REJECTED")))
                .containsExactly("S2-" + suffix, "S3-" + suffix);
    }

    @Test
    void filtraPorComunaSinDistinguirTildesNiMayusculas() throws Exception {
        String nunoa = local("Botillería Comuna " + suffix, "Irarrázaval 3000", "Ñuñoa");
        String stgo = local("Minimarket Comuna " + suffix, "Av. Matta 200", "Santiago");
        shipment("N-" + suffix, nunoa, day);
        shipment("S-" + suffix, stgo, day);

        assertThat(trackings(list("date=" + day + "&commune=nunoa"))).containsExactly("N-" + suffix);
        assertThat(trackings(list("date=" + day + "&commune=ÑUÑOA"))).containsExactly("N-" + suffix);
        assertThat(trackings(list("date=" + day + "&commune=SANTIAGO"))).containsExactly("S-" + suffix);
        // La comuna es coincidencia exacta, no parcial.
        assertThat(list("date=" + day + "&commune=nun").get("totalElements").asLong()).isZero();
    }

    @Test
    void buscaPorLocalDireccionTrackingOCodigoExterno() throws Exception {
        String roble = local("Botillería El Roble " + suffix, "Av. Providencia 2124", "Providencia");
        String otro = local("Bar La Esquina " + suffix, "Los Leones 45", "Providencia");
        shipment("PED-ROBLE-" + suffix, roble, day);
        shipment("PED-OTRO-" + suffix, otro, day);
        String d = "date=" + day + "&q=";

        assertThat(trackings(list(d + "botilleria el roble"))).containsExactly("PED-ROBLE-" + suffix); // sin tilde
        assertThat(trackings(list(d + "LOS LEONES"))).containsExactly("PED-OTRO-" + suffix);           // dirección
        assertThat(trackings(list(d + "ped-otro"))).containsExactly("PED-OTRO-" + suffix);             // tracking
        String externalCode = jdbc.queryForObject("SELECT external_code FROM customers WHERE id = ?::uuid", String.class, roble);
        assertThat(trackings(list(d + externalCode))).containsExactly("PED-ROBLE-" + suffix);         // código externo
        // Los comodines de LIKE se buscan como texto, no como "cualquier cosa".
        assertThat(list(d + "%").get("totalElements").asLong()).isZero();
        assertThat(list(d + "_").get("totalElements").asLong()).isZero();
    }

    @Test
    void parametrosInvalidosResponden400ConUnMensajeClaro() throws Exception {
        assertThat(call(withParams("date=" + day + "&from=" + day), 400).get("message").asText())
                .contains("no ambos");
        assertThat(call(withParams("from=" + day.plusDays(1) + "&to=" + day), 400).get("message").asText())
                .contains("es posterior a");
        assertThat(call(withParams("status=PERDIDO"), 400).get("message").asText())
                .startsWith("status inválido: PERDIDO");
        assertThat(call(withParams("size=0"), 400).get("message").asText()).isEqualTo("size debe estar entre 1 y 500");
        assertThat(call(withParams("size=501"), 400).get("message").asText()).isEqualTo("size debe estar entre 1 y 500");
        assertThat(call(withParams("page=-1"), 400).get("message").asText()).isEqualTo("page debe ser 0 o mayor");
        call(withParams("date=15-12-2026"), 400);
    }

    @Test
    void elOpenApiDocumentaLosFiltros() throws Exception {
        String apiDocs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode op = json.readTree(apiDocs).at("/paths/~1api~1shipments/get");
        List<String> params = new ArrayList<>();
        op.get("parameters").forEach(p -> params.add(p.get("name").asText()));
        assertThat(params).containsExactly("date", "from", "to", "status", "commune", "q", "page", "size");
        assertThat(op.get("summary").asText()).isEqualTo("Lista y filtra envíos (paginado)");
    }
}
