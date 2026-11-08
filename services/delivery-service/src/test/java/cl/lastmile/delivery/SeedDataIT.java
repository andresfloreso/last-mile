package cl.lastmile.delivery;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-01: carga la semilla real de infra/seed/delivery (perfil "seed") sobre un
 * PostgreSQL vacío y verifica volumen, patrones e idempotencia; luego arma la
 * ruta del día vía HTTP y comprueba que los clientes de mañana van primero.
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
class SeedDataIT {

    private static final String SEED_CUSTOMERS = "external_code LIKE 'SEED-C%'";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired TimeWindowLearningService learning;
    @Autowired DeliveryAttemptRepository attempts;

    @Value("${jwt.secret}") String jwtSecret;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private Map<String, String> patternByCustomerName() {
        // El patrón queda registrado en las notas de los envíos del día.
        return jdbc.query("""
                        SELECT DISTINCT c.full_name, substring(s.notes FROM 'patrón ([A-Z]+)') AS pattern
                        FROM shipments s JOIN customers c ON c.id = s.customer_id
                        WHERE s.scheduled_date = CURRENT_DATE AND s.source = 'seed'""",
                        (rs, i) -> Map.entry(rs.getString(1), rs.getString(2)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String dispatcherToken() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claims(Map.of("role", "DISPATCHER", "type", "access", "email", "despacho@lastmile.cl"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void cargaTreintaClientesConCoordenadasEnSantiago() {
        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS)).isEqualTo(30);
        assertThat(count("SELECT count(DISTINCT commune) FROM customers WHERE " + SEED_CUSTOMERS)).isEqualTo(30);
        // Bounding box aproximado del Gran Santiago.
        assertThat(count("""
                SELECT count(*) FROM customers WHERE %s
                  AND latitude BETWEEN -33.70 AND -33.30 AND longitude BETWEEN -70.85 AND -70.45
                """.formatted(SEED_CUSTOMERS))).isEqualTo(30);
    }

    @Test
    void cargaHistorialDeSesentaDiasYCincuentaEnviosDeHoy() {
        assertThat(count("SELECT count(*) FROM delivery_attempts")).isEqualTo(600);
        assertThat(count("""
                SELECT (max(attempted_at)::date - min(attempted_at)::date) FROM delivery_attempts
                """)).isBetween(55, 60);
        assertThat(count("SELECT count(*) FROM delivery_attempts WHERE attempted_at > now()")).isZero();
        assertThat(count("SELECT count(*) FROM shipments WHERE scheduled_date = CURRENT_DATE")).isEqualTo(50);
    }

    @Test
    void elMotorRecuperaLosPatronesConocidos() {
        Map<String, String> patterns = patternByCustomerName();
        List<Map<String, Object>> customers = jdbc.queryForList(
                "SELECT id, full_name FROM customers WHERE " + SEED_CUSTOMERS);

        Map<String, Integer> expectedStart = Map.of("MORNING", 9, "MIDDAY", 12, "AFTERNOON", 15, "EVENING", 18);
        for (Map<String, Object> c : customers) {
            String name = (String) c.get("full_name");
            var profile = learning.learn((UUID) c.get("id"), attempts);
            assertThat(profile.recommendedStartHour())
                    .as("franja aprendida para %s (%s)", name, patterns.get(name))
                    .isEqualTo(expectedStart.get(patterns.get(name)));
            assertThat(profile.confidence()).isEqualTo(1.0);
        }

        // Cliente A (mañana) evita la tarde; cliente B (noche) evita el día.
        UUID clienteA = UUID.fromString("5eed0000-0000-4000-8000-000000000001");
        UUID clienteB = UUID.fromString("5eed0000-0000-4000-8000-000000000004");
        assertThat(learning.learn(clienteA, attempts).hoursToAvoid()).contains(14, 15, 16);
        assertThat(learning.learn(clienteB, attempts).hoursToAvoid()).contains(11, 15, 17);
    }

    @Test
    void esIdempotente() {
        int customers = count("SELECT count(*) FROM customers");
        int shipments = count("SELECT count(*) FROM shipments");
        int attemptsCount = count("SELECT count(*) FROM delivery_attempts");

        flyway.migrate(); // vuelve a ejecutar el callback afterMigrate

        assertThat(count("SELECT count(*) FROM customers")).isEqualTo(customers);
        assertThat(count("SELECT count(*) FROM shipments")).isEqualTo(shipments);
        assertThat(count("SELECT count(*) FROM delivery_attempts")).isEqualTo(attemptsCount);
    }

    @Test
    void laRutaDelDiaOrdenaPrimeroALosClientesDeManana() throws Exception {
        String body = mvc.perform(post("/api/routes/build")
                        .header("Authorization", "Bearer " + dispatcherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","depotLat":-33.4489,"depotLng":-70.6693}""".formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode route = json.readTree(body);
        assertThat(route).hasSize(50);

        Map<String, String> patterns = patternByCustomerName();
        int morningStops = count("""
                SELECT count(*) FROM shipments
                WHERE scheduled_date = CURRENT_DATE AND notes LIKE '%patrón MORNING%'""");
        assertThat(morningStops).isPositive();

        List<String> firstPatterns = new ArrayList<>();
        List<LocalTime> windowStarts = new ArrayList<>();
        for (JsonNode stop : route) {
            windowStarts.add(LocalTime.parse(stop.get("windowStart").asText()));
            if (firstPatterns.size() < morningStops) {
                firstPatterns.add(patterns.get(stop.get("customerName").asText()));
            }
        }

        // Las primeras paradas son exactamente los envíos de clientes de mañana...
        assertThat(Set.copyOf(firstPatterns)).containsExactly("MORNING");
        assertThat(windowStarts.get(0)).isEqualTo(LocalTime.of(9, 0));
        // ...y el resto de la ruta respeta el orden de franjas (mediodía, tarde, noche).
        assertThat(windowStarts).isSorted();
        assertThat(windowStarts.get(windowStarts.size() - 1)).isEqualTo(LocalTime.of(18, 0));
    }
}
