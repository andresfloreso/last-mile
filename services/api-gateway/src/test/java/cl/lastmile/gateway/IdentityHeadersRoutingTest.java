package cl.lastmile.gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #9: el gateway nunca deja pasar los X-User-Id / X-User-Role que manda el
 * cliente. Levanta el gateway real con sus rutas apuntando a un servidor eco
 * local, que anota los headers que efectivamente recibe el servicio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityHeadersRoutingTest {

    /** Headers que recibió el "servicio" en la última request, por nombre en minúsculas. */
    static final Map<String, List<String>> RECEIVED = new ConcurrentHashMap<>();
    static final HttpServer BACKEND = startBackend();

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECEIVED.clear();
                exchange.getRequestHeaders().forEach((name, values) -> RECEIVED.put(name.toLowerCase(), List.copyOf(values)));
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + BACKEND.getAddress().getPort();
        registry.add("AUTH_SERVICE_URI", () -> url);
        registry.add("DELIVERY_SERVICE_URI", () -> url);
        registry.add("IMPORT_SERVICE_URI", () -> url);
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop(0);
    }

    @Autowired WebTestClient client;
    @Value("${jwt.secret}") String secret;

    @BeforeEach
    void reset() {
        RECEIVED.clear();
    }

    private String accessToken(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of("role", role, "type", "access"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void enUnaRutaPublicaLosHeadersDelClienteNoLleganAlServicio() {
        client.get().uri("/api/tracking/PED-1")
                .header("X-User-Id", "otro-usuario")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECEIVED).isNotEmpty();
        assertThat(RECEIVED).doesNotContainKeys("x-user-id", "x-user-role");
    }

    @Test
    void enUnaRutaPublicaTampocoPasanConOtraCapitalizacion() {
        client.post().uri("/api/auth/login")
                .header("x-user-role", "ADMIN")
                .header("X-USER-ID", "otro-usuario")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECEIVED).doesNotContainKeys("x-user-id", "x-user-role");
    }

    @Test
    void enUnaRutaProtegidaSoloLlegaLaIdentidadDelJwt() {
        client.get().uri("/api/shipments?date=2026-12-01")
                .header("Authorization", "Bearer " + accessToken("user-real", "DRIVER"))
                .header("X-User-Id", "otro-usuario")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECEIVED.get("x-user-id")).containsExactly("user-real");
        assertThat(RECEIVED.get("x-user-role")).containsExactly("DRIVER");
    }

    @Test
    void sinTokenUnaRutaProtegidaNoLlegaAlServicio() {
        client.get().uri("/api/shipments")
                .header("X-User-Id", "otro-usuario")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(RECEIVED).isEmpty();
    }
}
