package cl.lastmile.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #44: denegar por defecto. CLIENT (el rol del antiguo registro público) recibe 403 en
 * todo lo que no es público, y cada endpoint de ShipmentController define sus roles.
 *
 * Las requests de CLIENT llevan ids y cuerpos válidos: el 403 sale de la autorización,
 * no de una validación que fallaría antes.
 */
@SpringBootTest(properties = "geocoding.nominatim-url=http://localhost:1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationIT {

    private static final String ID = "5eed0000-0000-4000-8000-0000000000aa";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
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

    /** Todos los endpoints de ShipmentController, con un cuerpo válido. */
    static Stream<Arguments> shipmentEndpoints() {
        return Stream.of(
                Arguments.of("POST", "/api/shipments/customers", """
                        {"fullName":"Local","address":"Calle 1","latitude":-33.4,"longitude":-70.6}"""),
                Arguments.of("POST", "/api/shipments", """
                        {"trackingCode":"AUTH-1","customerId":"%s","scheduledDate":"2026-12-01"}""".formatted(ID)),
                Arguments.of("GET", "/api/shipments?date=2026-12-01", null),
                // HU-10: listado paginado con filtros (mismo endpoint, mismos roles)
                Arguments.of("GET", "/api/shipments?status=PENDING&commune=Providencia&q=roble&page=0&size=5", null),
                Arguments.of("POST", "/api/shipments/attempts", """
                        {"shipmentId":"%s","customerId":"%s","outcome":"ACCEPTED"}""".formatted(ID, ID)),
                Arguments.of("PATCH", "/api/shipments/" + ID, "{\"label\":\"x\"}"),
                Arguments.of("POST", "/api/shipments/" + ID + "/duplicate?newTrackingCode=AUTH-2", null),
                Arguments.of("DELETE", "/api/shipments/" + ID, null));
    }

    /** El resto de lo no público en delivery-service. */
    static Stream<Arguments> otherProtectedEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/api/customers/" + ID, null),
                Arguments.of("GET", "/api/customers/" + ID + "/time-window", null),
                Arguments.of("PUT", "/api/customers/" + ID + "/declared-window", "{\"start\":\"09:00\",\"end\":\"12:00\"}"),
                Arguments.of("PUT", "/api/customers/" + ID + "/location", "{\"latitude\":-33.4,\"longitude\":-70.6}"),
                Arguments.of("GET", "/api/geocoding/search?q=providencia", null),
                Arguments.of("POST", "/api/routes/build", "{\"date\":\"2026-12-01\",\"depotLat\":-33.4,\"depotLng\":-70.6}"),
                Arguments.of("GET", ROUTE_GET, null),
                Arguments.of("GET", "/api/no-existe", null));
    }

    /** HU-32: la ruta guardada de una fecha sin envíos (lista vacía). */
    private static final String ROUTE_GET = "/api/routes?date=2031-03-03&depotLat=-33.4&depotLng=-70.6";

    @ParameterizedTest(name = "{0} → GET /api/routes")
    @ValueSource(strings = {"ADMIN", "DISPATCHER", "OPERATOR"})
    void laRutaGuardadaLaVenDespachoAdministracionYOperaciones(String role) throws Exception {
        assertThat(status(role, "GET", ROUTE_GET, null)).isEqualTo(200);
    }

    @ParameterizedTest(name = "{0} → GET /api/routes")
    @ValueSource(strings = {"DRIVER", "CLIENT"})
    void conductorYClienteNoVenLaRutaGuardada(String role) throws Exception {
        assertThat(status(role, "GET", ROUTE_GET, null)).isEqualTo(403);
    }

    @Test
    void operatorNoArmaLaRuta() throws Exception {
        assertThat(status("OPERATOR", "POST", "/api/routes/build", "{\"date\":\"2031-03-03\",\"depotLat\":-33.4,\"depotLng\":-70.6}"))
                .isEqualTo(403);
    }

    private int status(String role, String method, String url, String body) throws Exception {
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(method), url);
        if (body != null) req.content(body);
        return mvc.perform(as(role, req)).andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "CLIENT → {0} {1}")
    @MethodSource({"shipmentEndpoints", "otherProtectedEndpoints"})
    void clientRecibe403EnTodoLoQueNoEsPublico(String method, String url, String body) throws Exception {
        assertThat(status("CLIENT", method, url, body)).isEqualTo(403);
    }

    @ParameterizedTest(name = "rol desconocido → {0} {1}")
    @MethodSource("shipmentEndpoints")
    void unRolDesconocidoTambienRecibe403(String method, String url, String body) throws Exception {
        assertThat(status("SUPERUSER", method, url, body)).isEqualTo(403);
    }

    @ParameterizedTest(name = "DRIVER → {0} {1}")
    @MethodSource("shipmentEndpoints")
    void driverSoloRegistraIntentosDeEntrega(String method, String url, String body) throws Exception {
        int status = status("DRIVER", method, url, body);
        if (url.equals("/api/shipments/attempts")) {
            assertThat(status).as("intentos").isNotEqualTo(403); // pasa la autorización (404: el envío no existe)
        } else {
            assertThat(status).isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "{0} → GET /api/shipments paginado")
    @ValueSource(strings = {"ADMIN", "DISPATCHER", "OPERATOR"})
    void elListadoPaginadoEsParaLosRolesDeOficina(String role) throws Exception {
        assertThat(status(role, "GET", "/api/shipments?status=PENDING&page=0&size=5", null)).isEqualTo(200);
    }

    @ParameterizedTest(name = "OPERATOR → {0} {1}")
    @MethodSource("shipmentEndpoints")
    void losRolesDeOficinaPasanLaAutorizacion(String method, String url, String body) throws Exception {
        assertThat(status("OPERATOR", method, url, body)).isNotIn(401, 403);
    }

    @Test
    void elSeguimientoYElHealthSiguenSiendoPublicos() throws Exception {
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isEqualTo(200);
        // Sin token: el seguimiento público responde (404 porque el código no existe), no 401/403.
        assertThat(mvc.perform(get("/api/tracking/NO-EXISTE")).andReturn().getResponse().getStatus()).isNotIn(401, 403);
        // Con token de CLIENT también.
        assertThat(mvc.perform(as("CLIENT", get("/api/tracking/NO-EXISTE"))).andReturn().getResponse().getStatus())
                .isNotIn(401, 403);
    }

    @Test
    void sinTokenNoHayAccesoAEnvios() throws Exception {
        int status = mvc.perform(post("/api/shipments/attempts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void elOpenApiDocumentaLos403() throws Exception {
        String apiDocs = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(apiDocs).at("/paths/~1api~1shipments~1attempts/post/responses/403/description").asText())
                .isEqualTo("Rol sin permiso (solo ADMIN, DISPATCHER, OPERATOR o DRIVER)");
        assertThat(json.readTree(apiDocs).at("/paths/~1api~1shipments~1{shipmentId}/delete/responses/403/description").asText())
                .isEqualTo("Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)");
    }
}
