package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome.ACCEPTED;
import static cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome.REJECTED_NOT_HOME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-12: decaimiento temporal de los intentos y caché del perfil. */
class TimeWindowDecayAndCacheTest {

    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
    private static final LocalDate TODAY = LocalDate.of(2027, 1, 15);
    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(20, 0).atZone(SANTIAGO).toInstant(), ZoneOffset.UTC);

    private final UUID customerId = UUID.randomUUID();

    private static DeliveryAttempt at(int daysAgo, int hour, AttemptOutcome outcome) {
        return DeliveryAttempt.builder()
                .attemptedAt(TODAY.minusDays(daysAgo).atTime(hour, 30).atZone(SANTIAGO).toInstant())
                .outcome(outcome)
                .build();
    }

    /**
     * Un local que recibía de 9 a 11 durante meses (12 visitas hace 60–120 días) y cambió de hábito: las
     * últimas 6 visitas, del último mes, recibió de 15 a 17 y lo encontraron cerrado de 9 a 11.
     */
    private List<DeliveryAttempt> cambioDeHabito() {
        List<DeliveryAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < 12; i++) attempts.add(at(60 + i * 5, 9 + i % 2, ACCEPTED));
        for (int i = 0; i < 4; i++) attempts.add(at(3 + i * 7, 15 + i % 2, ACCEPTED));
        for (int i = 0; i < 2; i++) attempts.add(at(5 + i * 7, 9 + i, REJECTED_NOT_HOME));
        return attempts;
    }

    private static TimeWindowLearningService withHalfLife(double days, TimeWindowProfileCache cache) {
        return new TimeWindowLearningService(days, CLOCK, cache);
    }

    @Test
    void sinDecaimientoSigueEnElHabitoViejo() {
        CustomerTimeWindowProfile profile = withHalfLife(0, TimeWindowProfileCache.NONE).learn(customerId, cambioDeHabito());

        assertThat(profile.recommendedStartHour()).isEqualTo(9);
    }

    @Test
    void conLaVidaMediaPorDefectoAprendeElHabitoNuevo() {
        // 90 días, el valor por defecto de time-window.decay.half-life-days.
        CustomerTimeWindowProfile profile = withHalfLife(90, TimeWindowProfileCache.NONE).learn(customerId, cambioDeHabito());

        assertThat(profile.recommendedStartHour()).isEqualTo(15);
        assertThat(profile.recommendedEndHour()).isEqualTo(17);
        // Los conteos siguen siendo visitas reales (HU-11): 4 de 4 en la ventana, 18 en total.
        assertThat(profile.acceptedInWindow()).isEqualTo(4);
        assertThat(profile.attemptsInWindow()).isEqualTo(4);
        assertThat(profile.sampleSize()).isEqualTo(18);
    }

    @Test
    void elPesoCaeALaMitadEnCadaVidaMedia() {
        TimeWindowLearningService service = withHalfLife(45, TimeWindowProfileCache.NONE);
        Instant now = CLOCK.instant();

        assertThat(service.weight(now, now)).isEqualTo(1.0);
        assertThat(service.weight(now.minusSeconds(45L * 86_400), now)).isCloseTo(0.5, within(1e-9));
        assertThat(service.weight(now.minusSeconds(90L * 86_400), now)).isCloseTo(0.25, within(1e-9));
        assertThat(service.weight(now.plusSeconds(86_400), now)).as("fecha futura").isEqualTo(1.0);
        assertThat(withHalfLife(0, TimeWindowProfileCache.NONE).weight(now.minusSeconds(900L * 86_400), now))
                .as("sin decaimiento").isEqualTo(1.0);
    }

    @Test
    void conPerfilEnCacheNoLeeLosIntentos() {
        TimeWindowProfileCache cache = mock(TimeWindowProfileCache.class);
        DeliveryAttemptRepository repository = mock(DeliveryAttemptRepository.class);
        CustomerTimeWindowProfile cached = withHalfLife(0, TimeWindowProfileCache.NONE).learn(customerId, cambioDeHabito());
        when(cache.get(customerId)).thenReturn(Optional.of(cached));

        CustomerTimeWindowProfile profile = withHalfLife(30, cache).learn(customerId, repository);

        assertThat(profile).isSameAs(cached);
        verify(repository, never()).findByCustomerId(customerId);
    }

    @Test
    void sinPerfilEnCacheLoCalculaYLoGuarda() {
        TimeWindowProfileCache cache = mock(TimeWindowProfileCache.class);
        DeliveryAttemptRepository repository = mock(DeliveryAttemptRepository.class);
        when(cache.get(customerId)).thenReturn(Optional.empty());
        when(repository.findByCustomerId(customerId)).thenReturn(cambioDeHabito());

        CustomerTimeWindowProfile profile = withHalfLife(30, cache).learn(customerId, repository);

        assertThat(profile.recommendedStartHour()).isEqualTo(15);
        verify(cache).put(profile);
    }

    @Test
    void olvidarInvalidaElPerfilDelLocal() {
        TimeWindowProfileCache cache = mock(TimeWindowProfileCache.class);

        withHalfLife(30, cache).forget(customerId);

        verify(cache).evict(customerId);
    }
}
