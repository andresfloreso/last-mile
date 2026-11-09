package cl.lastmile.delivery.geo;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nominatim falso para los tests (HU-09): sirve {@code /search} en un puerto
 * local, sin red. Responde según la palabra clave registrada que aparezca en
 * la consulta y anota cada request con su instante de llegada.
 */
public final class FakeNominatim implements AutoCloseable {

    /** Una consulta recibida: texto de {@code q} y {@link System#nanoTime()} al llegar. */
    public record Request(String query, long nanoTime) {}

    private final HttpServer server;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile long delayMillis;

    public FakeNominatim() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/search", exchange -> {
            String q = param(exchange.getRequestURI().getRawQuery(), "q");
            requests.add(new Request(q, System.nanoTime()));

            String lower = q.toLowerCase(Locale.ROOT);
            int status = 200;
            String body = "[]";
            for (var entry : statuses.entrySet()) {
                if (lower.contains(entry.getKey())) status = entry.getValue();
            }
            for (var entry : responses.entrySet()) {
                if (lower.contains(entry.getKey())) body = entry.getValue();
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Las consultas que contengan {@code keyword} devuelven un resultado con ese place_rank. */
    public FakeNominatim found(String keyword, double lat, double lon, int placeRank) {
        responses.put(keyword.toLowerCase(Locale.ROOT),
                "[{\"lat\":\"%s\",\"lon\":\"%s\",\"importance\":0.4,\"place_rank\":%d,\"display_name\":\"%s\"}]"
                        .formatted(lat, lon, placeRank, keyword));
        return this;
    }

    /** Las consultas que contengan {@code keyword} responden con ese código HTTP. */
    public FakeNominatim failing(String keyword, int status) {
        statuses.put(keyword.toLowerCase(Locale.ROOT), status);
        return this;
    }

    /** Demora cada respuesta, como un Nominatim lento o una red con latencia. */
    public FakeNominatim respondingAfter(long millis) {
        this.delayMillis = millis;
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public long requestsContaining(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return requests.stream().filter(r -> r.query().toLowerCase(Locale.ROOT).contains(lower)).count();
    }

    private static String param(String rawQuery, String name) {
        if (rawQuery == null) return "";
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
