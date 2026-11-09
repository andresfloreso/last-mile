package cl.lastmile.delivery.geo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-09: rate limit, caché por dirección y confianza, contra un Nominatim falso (sin red). */
class GeocodingServiceTest {

    private final FakeNominatim nominatim = new FakeNominatim();

    @AfterEach
    void stop() {
        nominatim.close();
    }

    private GeocodingService service(Duration minInterval) {
        return new GeocodingService(nominatim.url(), "LastMileTest/1.0", minInterval, 28, 100);
    }

    @Test
    void resultadoANivelDeNumeroEsPreciso() {
        nominatim.found("providencia 2124", -33.4263, -70.6105, 30);

        Optional<GeocodingService.GeoPoint> point = service(Duration.ZERO).geocode("Av. Providencia 2124", "Providencia");

        assertThat(point).isPresent();
        assertThat(point.get().latitude()).isEqualTo(-33.4263);
        assertThat(point.get().longitude()).isEqualTo(-70.6105);
        assertThat(point.get().precise()).isTrue();
        assertThat(nominatim.requests()).singleElement()
                .extracting(FakeNominatim.Request::query).isEqualTo("Av. Providencia 2124, Providencia, Chile");
    }

    @Test
    void resultadoSoloDeCalleOComunaQuedaPorRevisar() {
        nominatim.found("calle larga", -33.40, -70.60, 26).found("sin numero", -33.45, -70.66, 16);
        GeocodingService service = service(Duration.ZERO);

        assertThat(service.geocode("Calle Larga", "Maipú")).get()
                .extracting(GeocodingService.GeoPoint::precise).isEqualTo(false);
        assertThat(service.geocode("Sin numero", null)).get()
                .extracting(GeocodingService.GeoPoint::precise).isEqualTo(false);
    }

    @Test
    void unaDireccionRepetidaNoVuelveAConsultarNominatim() {
        nominatim.found("providencia 2124", -33.4263, -70.6105, 30);
        GeocodingService service = service(Duration.ZERO);

        service.geocode("Av. Providencia 2124", "Providencia");
        service.geocode("  av.  providencia 2124 ", "PROVIDENCIA");
        service.geocode("Av. Providencia 2124", "Providencia");

        assertThat(nominatim.requests()).hasSize(1);
    }

    @Test
    void sinResultadosTambienSeCachea() {
        GeocodingService service = service(Duration.ZERO);

        assertThat(service.geocode("Dirección inexistente 999", "Ñuñoa")).isEmpty();
        assertThat(service.geocode("Dirección inexistente 999", "Ñuñoa")).isEmpty();

        assertThat(nominatim.requests()).hasSize(1);
    }

    @Test
    void unErrorDeNominatimNoSeCacheaYSeReintenta() {
        nominatim.failing("caida", 503);
        GeocodingService service = service(Duration.ZERO);

        assertThat(service.geocode("Calle caida 1", null)).isEmpty();
        assertThat(service.geocode("Calle caida 1", null)).isEmpty();

        assertThat(nominatim.requests()).hasSize(2);
    }

    @Test
    void respetaUnRequestPorSegundo() {
        GeocodingService service = service(Duration.ofSeconds(1));

        service.geocode("Calle 1", null);
        service.geocode("Calle 2", null);
        service.geocode("Calle 3", null);

        List<FakeNominatim.Request> requests = nominatim.requests();
        assertThat(requests).hasSize(3);
        for (int i = 1; i < requests.size(); i++) {
            long gap = requests.get(i).nanoTime() - requests.get(i - 1).nanoTime();
            assertThat(Duration.ofNanos(gap)).isGreaterThanOrEqualTo(Duration.ofMillis(990));
        }
    }

    @Test
    void elSegundoDeEsperaCuentaDesdeQueTerminaLaConsultaAnterior() {
        nominatim.respondingAfter(400);
        GeocodingService service = service(Duration.ofSeconds(1));

        service.geocode("Lenta 1", null);
        service.geocode("Lenta 2", null);

        List<FakeNominatim.Request> requests = nominatim.requests();
        long gap = requests.get(1).nanoTime() - requests.get(0).nanoTime();
        // 400 ms de respuesta + 1 s de espera: aunque la red demore, nunca llegan dos requests en menos de 1 s.
        assertThat(Duration.ofNanos(gap)).isGreaterThanOrEqualTo(Duration.ofMillis(1390));
    }

    @Test
    void elLimiteSeRespetaConVariasPeticionesConcurrentes() throws Exception {
        GeocodingService service = service(Duration.ofMillis(300));
        List<Thread> threads = List.of(
                Thread.ofVirtual().start(() -> service.geocode("Hilo A", null)),
                Thread.ofVirtual().start(() -> service.geocode("Hilo B", null)),
                Thread.ofVirtual().start(() -> service.geocode("Hilo C", null)));
        for (Thread t : threads) t.join();

        List<Long> times = nominatim.requests().stream().map(FakeNominatim.Request::nanoTime).sorted().toList();
        assertThat(times).hasSize(3);
        for (int i = 1; i < times.size(); i++) {
            assertThat(Duration.ofNanos(times.get(i) - times.get(i - 1))).isGreaterThanOrEqualTo(Duration.ofMillis(290));
        }
    }

    @Test
    void laClaveDeCacheIgnoraMayusculasYEspacios() {
        assertThat(GeocodingService.cacheKey("  Av.  Providencia   2124 , Chile "))
                .isEqualTo("av. providencia 2124, chile");
    }
}
