package cl.lastmile.delivery;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.model.DeclaredWindow;
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
 * HU-01 / HU-25: carga la semilla real de infra/seed/delivery (perfil "seed")
 * sobre un PostgreSQL vacío y verifica volumen, patrones, modulación e
 * idempotencia; luego arma la ruta del día vía HTTP y comprueba que los
 * locales que reciben en la mañana van primero.
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
    /** HU-31: zona de reparto compacta, de 3 comunas contiguas. */
    private static final String ZONE = "('Santiago', 'Providencia', 'Ñuñoa')";
    /** Caja de la zona (unos 6 × 6 km): del centro de Santiago a Ñuñoa. */
    private static final String IN_ZONE_BOX =
            "latitude BETWEEN -33.47 AND -33.41 AND longitude BETWEEN -70.66 AND -70.585";

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
        // El patrón queda registrado en las notas de los envíos semilla (históricos y del día).
        return jdbc.query("""
                        SELECT DISTINCT c.full_name, substring(s.notes FROM 'patrón ([A-Z]+)') AS pattern
                        FROM shipments s JOIN customers c ON c.id = s.customer_id
                        WHERE s.source = 'seed' AND s.notes LIKE '%patrón%'""",
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
    void cargaTreintaLocalesComercialesConTipoYModulacion() {
        Map<String, Integer> byType = jdbc.query(
                        "SELECT business_type, count(*) FROM customers WHERE " + SEED_CUSTOMERS + " GROUP BY business_type",
                        (rs, i) -> Map.entry(rs.getString(1), rs.getInt(2)))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(byType).containsOnlyKeys("BOTILLERIA", "MINIMARKET", "BAR", "RESTAURANT");
        assertThat(byType.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(30);

        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS
                + " AND declared_window_start IS NOT NULL AND declared_window_end IS NOT NULL")).isEqualTo(30);
        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS
                + " AND declared_window2_start IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void elMotorRecuperaLaVentanaRealYNoLaModulacionDeclarada() {
        Map<String, String> patterns = patternByCustomerName();
        Map<String, Integer> realStart = Map.of("MORNING", 9, "MIDDAY", 12, "AFTERNOON", 15, "EVENING", 18);

        List<Map<String, Object>> locales = jdbc.queryForList("""
                SELECT id, full_name, declared_window_start, declared_window_end,
                       declared_window2_start, declared_window2_end
                FROM customers WHERE %s""".formatted(SEED_CUSTOMERS));

        int declaranOtroHorario = 0;
        for (Map<String, Object> local : locales) {
            String name = (String) local.get("full_name");
            DeclaredWindow declared = new DeclaredWindow(
                    time(local.get("declared_window_start")), time(local.get("declared_window_end")),
                    time(local.get("declared_window2_start")), time(local.get("declared_window2_end")));

            int learned = learning.learn((UUID) local.get("id"), attempts).recommendedStartHour();
            // El motor aprende la ventana real, sin importar lo que el local declara.
            assertThat(learned).as("ventana real aprendida para %s", name)
                    .isEqualTo(realStart.get(patterns.get(name)));

            if (!declared.contains(LocalTime.of(learned, 0))) {
                declaranOtroHorario++;
            }
        }

        // Al menos un tercio de los locales declara una modulación que no coincide con su ventana real.
        assertThat(declaranOtroHorario).isGreaterThanOrEqualTo(10);

        // Local B (Bar La Esquina de Ñuñoa): declara 09–12, pero recibe después de las 19 h.
        Map<String, Object> barB = jdbc.queryForMap(
                "SELECT declared_window_start, declared_window_end FROM customers WHERE id = ?",
                UUID.fromString("5eed0000-0000-4000-8000-000000000004"));
        assertThat(time(barB.get("declared_window_start"))).isEqualTo(LocalTime.of(9, 0));
        assertThat(time(barB.get("declared_window_end"))).isEqualTo(LocalTime.of(12, 0));
        assertThat(learning.learn(UUID.fromString("5eed0000-0000-4000-8000-000000000004"), attempts)
                .recommendedStartHour()).isEqualTo(18);
    }

    private static LocalTime time(Object sqlTime) {
        return sqlTime == null ? null : ((java.sql.Time) sqlTime).toLocalTime();
    }

    @Test
    void cargaTreintaLocalesEnUnaZonaCompactaDeTresComunas() {
        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS)).isEqualTo(30);
        // HU-31: los 30 locales están en la zona, 10 por comuna, dentro de una caja de unos 6 × 6 km.
        assertThat(jdbc.query("SELECT commune, count(*) FROM customers WHERE " + SEED_CUSTOMERS + " GROUP BY commune",
                (rs, i) -> Map.entry(rs.getString(1), rs.getInt(2))))
                .containsExactlyInAnyOrder(Map.entry("Santiago", 10), Map.entry("Providencia", 10), Map.entry("Ñuñoa", 10));
        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS + " AND " + IN_ZONE_BOX))
                .isEqualTo(30);
        // Direcciones reales y distintas, con número (sin "s/n") y coordenadas fijas en el SQL.
        assertThat(count("SELECT count(DISTINCT address) FROM customers WHERE " + SEED_CUSTOMERS)).isEqualTo(30);
        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS + " AND address !~ ' [0-9]+$'"))
                .as("toda dirección termina en su número").isZero();
        // Sin geocodificar en runtime: ninguno queda marcado para revisar.
        assertThat(count("SELECT count(*) FROM customers WHERE " + SEED_CUSTOMERS
                + " AND (latitude IS NULL OR geocode_status = 'REVIEW')")).isZero();
        // Dos locales no comparten el mismo punto (más de 50 m entre cualquier par).
        assertThat(count("""
                SELECT count(*) FROM customers a JOIN customers b ON a.id < b.id
                WHERE a.%1$s AND b.%1$s
                  AND 6371000 * 2 * asin(sqrt(power(sin(radians(b.latitude - a.latitude) / 2), 2)
                      + cos(radians(a.latitude)) * cos(radians(b.latitude))
                      * power(sin(radians(b.longitude - a.longitude) / 2), 2))) < 50
                """.formatted(SEED_CUSTOMERS))).isZero();
    }

    @Test
    void cargaHistorialDeSesentaDiasYCincuentaEnviosDeHoy() {
        assertThat(count("SELECT count(*) FROM delivery_attempts")).isEqualTo(600);
        assertThat(count("""
                SELECT (max(attempted_at)::date - min(attempted_at)::date) FROM delivery_attempts
                """)).isBetween(55, 60);
        assertThat(count("SELECT count(*) FROM delivery_attempts WHERE attempted_at > now()")).isZero();
        assertThat(count("SELECT count(*) FROM shipments WHERE scheduled_date = CURRENT_DATE")).isEqualTo(24);
        // Todos los envíos del día son de la zona, uno por local.
        assertThat(count("""
                SELECT count(DISTINCT s.customer_id) FROM shipments s JOIN customers c ON c.id = s.customer_id
                WHERE s.scheduled_date = CURRENT_DATE AND c.commune IN """ + ZONE)).isEqualTo(24);
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

        // Local A (minimarket, mañana) evita la tarde; local B (bar, noche) evita el día.
        UUID clienteA = UUID.fromString("5eed0000-0000-4000-8000-000000000001");
        UUID clienteB = UUID.fromString("5eed0000-0000-4000-8000-000000000004");
        assertThat(learning.learn(clienteA, attempts).hoursToAvoid()).contains(14, 15, 16);
        assertThat(learning.learn(clienteB, attempts).hoursToAvoid()).contains(11, 15, 17);
    }

    /** Los locales no reciben siempre en su ventana real: entre 70% y 95% de las visitas en ella. */
    @Test
    void dentroDeSuVentanaRealCadaLocalRecibeEntre70Y95PorCiento() {
        Map<String, Integer> realStart = Map.of("MORNING", 9, "MIDDAY", 12, "AFTERNOON", 15, "EVENING", 18);
        Map<String, String> patterns = patternByCustomerName();
        List<Map<String, Object>> customers = jdbc.queryForList(
                "SELECT id, full_name FROM customers WHERE " + SEED_CUSTOMERS);
        Set<Integer> distinctAccepted = new java.util.HashSet<>();

        for (Map<String, Object> c : customers) {
            String name = (String) c.get("full_name");
            int start = realStart.get(patterns.get(name));
            Map<String, Object> counts = jdbc.queryForMap("""
                    SELECT count(*) AS visits, count(*) FILTER (WHERE outcome = 'ACCEPTED') AS accepted
                    FROM delivery_attempts
                    WHERE customer_id = ?
                      AND extract(hour FROM attempted_at AT TIME ZONE 'America/Santiago') BETWEEN ? AND ?""",
                    c.get("id"), start, start + 1);
            long visits = (Long) counts.get("visits");
            long accepted = (Long) counts.get("accepted");
            assertThat(visits).as(name).isEqualTo(14);
            assertThat((double) accepted / visits).as("%s: %d de %d", name, accepted, visits).isBetween(0.70, 0.95);
            distinctAccepted.add((int) accepted);

            // El motor sigue aprendiendo la misma ventana con el ruido.
            assertThat(learning.learn((UUID) c.get("id"), attempts).recommendedStartHour()).as(name).isEqualTo(start);
        }
        // No todos iguales: hay locales de 10, 11, 12 y 13 de 14.
        assertThat(distinctAccepted).containsExactlyInAnyOrder(10, 11, 12, 13);
        // Los rechazos dentro de la ventana son "encargado no estaba".
        assertThat(count("SELECT count(*) FROM delivery_attempts WHERE rejection_reason = 'Encargado no estaba'"))
                .isEqualTo(75); // 1 + idx % 4 por local: 8 locales con 2, 8 con 3, 7 con 4 y 7 con 1
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
    void laRutaDelDiaOrdenaPrimeroALosLocalesDeLaManana() throws Exception {
        String body = mvc.perform(post("/api/routes/build")
                        .header("Authorization", "Bearer " + dispatcherToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","depotLat":-33.446393,"depotLng":-70.624979}""".formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode route = json.readTree(body);
        assertThat(route).hasSize(count("SELECT count(*) FROM shipments WHERE scheduled_date = CURRENT_DATE"));

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

        // Las primeras paradas son exactamente los envíos de locales que reciben en la mañana...
        assertThat(Set.copyOf(firstPatterns)).containsExactly("MORNING");
        assertThat(windowStarts.get(0)).isEqualTo(LocalTime.of(9, 0));
        // ...y el resto de la ruta respeta el orden de franjas (mediodía, tarde, noche).
        assertThat(windowStarts).isSorted();
        assertThat(windowStarts.get(windowStarts.size() - 1)).isEqualTo(LocalTime.of(18, 0));
    }
}
