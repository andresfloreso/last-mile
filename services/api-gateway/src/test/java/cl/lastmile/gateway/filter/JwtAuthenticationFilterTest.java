package cl.lastmile.gateway.filter;

import cl.lastmile.gateway.security.JwtSecrets;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-04: validación del JWT en el borde (api-gateway). */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "clave-de-prueba-de-al-menos-32-caracteres!!";
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    /** Cadena que recuerda la request que llegó al siguiente filtro. */
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    private static String token(String secret, long expiresInMs, Map<String, Object> claims) {
        return Jwts.builder()
                .subject("user-42")
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private static String accessToken(String role) {
        return token(SECRET, 60_000, Map.of("role", role, "type", "access"));
    }

    private MockServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private MockServerWebExchange runWithToken(String path, String token) {
        return run(MockServerHttpRequest.get(path).header("Authorization", "Bearer " + token).build());
    }

    private static String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test
    void lasRutasPublicasPasanSinToken() {
        for (String path : new String[]{"/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                "/api/tracking/PED-1", "/actuator/health"}) {
            forwarded.set(null);
            MockServerWebExchange exchange = run(MockServerHttpRequest.post(path).build());

            assertThat(forwarded.get()).as(path).isNotNull();
            assertThat(exchange.getResponse().getStatusCode()).as(path).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void unaRutaProtegidaSinTokenResponde401() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/shipments").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void unHeaderAuthorizationSinBearerResponde401() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/shipments")
                .header("Authorization", "Basic dXNlcjpwYXNz").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void unTokenValidoPasaYPropagaUsuarioYRol() {
        runWithToken("/api/shipments", accessToken("DISPATCHER"));

        assertThat(forwarded.get()).isNotNull();
        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-42");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("DISPATCHER");
    }

    @Test
    void unTokenVencidoResponde401() {
        MockServerWebExchange exchange = runWithToken("/api/shipments",
                token(SECRET, -1_000, Map.of("role", "ADMIN", "type", "access")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
        // #36: mensaje fijo, sin la hora del servidor ni el clock skew que trae jjwt.
        assertThat(body(exchange)).isEqualTo("{\"error\":\"Token expirado\"}");
    }

    @Test
    void unRefreshTokenNoSirveParaLlamarALaApi() {
        MockServerWebExchange exchange = runWithToken("/api/shipments", token(SECRET, 60_000, Map.of("type", "refresh")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void unTokenFirmadoConOtraClaveResponde401() {
        String forged = token("otra-clave-distinta-tambien-de-32-caracteres", 60_000, Map.of("role", "ADMIN", "type", "access"));

        MockServerWebExchange exchange = runWithToken("/api/shipments", forged);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
        // #36: no dice que la firma no coincide ni repite el mensaje de jjwt.
        assertThat(body(exchange)).isEqualTo("{\"error\":\"Token inválido\"}");
    }

    @Test
    void textoQueNoEsUnJwtResponde401() {
        MockServerWebExchange exchange = runWithToken("/api/shipments", "no-es-un-jwt");

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
        assertThat(body(exchange)).isEqualTo("{\"error\":\"Token inválido\"}");
    }

    @Test
    void ningunaFallaDeValidacionExponeElMensajeDeJjwt() {
        String[] tokens = {
                token(SECRET, -1_000, Map.of("type", "access")),
                token("otra-clave-distinta-tambien-de-32-caracteres", 60_000, Map.of("type", "access")),
                "no-es-un-jwt",
                accessToken("ADMIN") + "x",
                "",
        };
        for (String t : tokens) {
            String body = body(runWithToken("/api/shipments", t));
            assertThat(body).as(t)
                    .matches("\\{\"error\":\"Token (inválido|expirado)\"}")
                    .doesNotContainIgnoringCase("signature")
                    .doesNotContain("Current time", "clock skew", "JWT");
        }
    }

    @Test
    void seEjecutaAntesQueElEnrutamiento() {
        assertThat(filter.getOrder()).isNegative();
    }

    @Test
    void noArrancaConUnaClaveCorta() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter("corta"))
                .isInstanceOf(JwtSecrets.InvalidJwtSecretException.class);
    }
}
