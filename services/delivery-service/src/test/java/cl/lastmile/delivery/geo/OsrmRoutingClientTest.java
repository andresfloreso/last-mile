package cl.lastmile.delivery.geo;

import cl.lastmile.delivery.geo.OsrmRoutingClient.Leg;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-14: OSRM /route contra un servidor falso, sin red. */
class OsrmRoutingClientTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private OsrmRoutingClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new OsrmRoutingClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static final List<double[]> POINTS = List.of(
            new double[]{-33.45, -70.66}, new double[]{-33.44, -70.65}, new double[]{-33.43, -70.64});

    @Test
    void leeUnTramoPorParDePuntosConDuracionDistanciaYGeometria() {
        body.set("""
                {"code":"Ok","routes":[{"legs":[
                  {"duration":600.4,"distance":4200.0,"steps":[
                    {"geometry":{"coordinates":[[-70.66,-33.45],[-70.655,-33.445]]}},
                    {"geometry":{"coordinates":[[-70.655,-33.445],[-70.65,-33.44]]}}]},
                  {"duration":300.0,"distance":1500.5,"steps":[
                    {"geometry":{"coordinates":[[-70.65,-33.44],[-70.64,-33.43]]}}]}
                ]}]}""");

        Optional<List<Leg>> legs = client.route(POINTS);

        assertThat(legs).isPresent();
        assertThat(legs.get()).hasSize(2);
        Leg first = legs.get().get(0);
        assertThat(first.durationSeconds()).isEqualTo(600.4);
        assertThat(first.distanceMeters()).isEqualTo(4200.0);
        // Pasa de (lon, lat) a (lat, lng) y no repite el punto donde se unen los pasos.
        assertThat(first.path()).hasSize(3);
        assertThat(first.path().get(0)).containsExactly(-33.45, -70.66);
        assertThat(first.path().get(2)).containsExactly(-33.44, -70.65);
        // OSRM recibe lon,lat en el orden de la ruta.
        assertThat(lastPath.get()).startsWith("/route/v1/driving/-70.66,-33.45;-70.65,-33.44;-70.64,-33.43?")
                .contains("steps=true", "geometries=geojson");
    }

    @Test
    void aceptaRespuestasGrandesComoLasDeUnaRutaDeCincuentaParadas() {
        // Con 50 paradas OSRM devuelve ~1 MB de geometría; el buffer por defecto de WebClient es 256 KB.
        StringBuilder coords = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            if (i > 0) coords.append(',');
            coords.append("[-70.6").append(i).append(",-33.4").append(i).append(']');
        }
        String bigLeg = "{\"duration\":60,\"distance\":500,\"steps\":[{\"geometry\":{\"coordinates\":[" + coords + "]}}]}";
        body.set("{\"code\":\"Ok\",\"routes\":[{\"legs\":[" + bigLeg + "," + bigLeg + "]}]}");
        assertThat(body.get().length()).isGreaterThan(256 * 1024);

        Optional<List<Leg>> legs = client.route(POINTS);

        assertThat(legs).isPresent();
        assertThat(legs.get().get(0).path()).hasSize(20_000);
    }

    @Test
    void unCodigoDistintoDeOkEsSinRuta() {
        body.set("{\"code\":\"NoRoute\",\"routes\":[]}");
        assertThat(client.route(POINTS)).isEmpty();
    }

    @Test
    void unaCantidadDeTramosQueNoCuadraEsSinRuta() {
        body.set("{\"code\":\"Ok\",\"routes\":[{\"legs\":[{\"duration\":1,\"distance\":1,\"steps\":[]}]}]}");
        assertThat(client.route(POINTS)).isEmpty();
    }

    @Test
    void unErrorHttpEsSinRuta() {
        status.set(429);
        body.set("{\"message\":\"Too Many Requests\"}");
        assertThat(client.route(POINTS)).isEmpty();
    }

    @Test
    void unServidorCaidoEsSinRuta() {
        assertThat(new OsrmRoutingClient("http://127.0.0.1:1").route(POINTS)).isEmpty();
    }

    @Test
    void conUnSoloPuntoNoHayTramos() {
        assertThat(client.route(List.of(new double[]{-33.45, -70.66}))).contains(List.of());
        assertThat(lastPath.get()).isNull();
    }
}
