package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome.ACCEPTED;
import static cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome.REJECTED_NOT_HOME;
import static cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome.REJECTED_REFUSED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HU-04: pruebas base del aprendizaje de la ventana real (sin modificar el servicio). */
class TimeWindowLearningServiceTest {

    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    private final TimeWindowLearningService service = new TimeWindowLearningService();
    private final UUID customerId = UUID.randomUUID();

    /** Intento a la hora local de Santiago indicada (minuto 30, para no caer en el borde). */
    private static DeliveryAttempt at(int hour, AttemptOutcome outcome) {
        return DeliveryAttempt.builder()
                .attemptedAt(DAY.atTime(hour, 30).atZone(SANTIAGO).toInstant())
                .outcome(outcome)
                .build();
    }

    private static List<DeliveryAttempt> repeat(int times, int hour, AttemptOutcome outcome) {
        List<DeliveryAttempt> list = new ArrayList<>();
        for (int i = 0; i < times; i++) list.add(at(hour, outcome));
        return list;
    }

    @Test
    void sinHistorialUsaLaVentanaComercialConConfianzaCero() {
        CustomerTimeWindowProfile profile = service.learn(customerId, List.of());

        assertThat(profile.customerId()).isEqualTo(customerId);
        assertThat(profile.recommendedStartHour()).isEqualTo(9);
        assertThat(profile.recommendedEndHour()).isEqualTo(18);
        assertThat(profile.hoursToAvoid()).isEmpty();
        assertThat(profile.sampleSize()).isZero();
        assertThat(profile.confidence()).isZero();
    }

    @Test
    void recomiendaElBloqueDeDosHorasConMasEntregasAceptadas() {
        List<DeliveryAttempt> attempts = new ArrayList<>();
        attempts.addAll(repeat(2, 14, ACCEPTED));
        attempts.addAll(repeat(2, 15, ACCEPTED));
        attempts.add(at(10, ACCEPTED));

        CustomerTimeWindowProfile profile = service.learn(customerId, attempts);

        assertThat(profile.recommendedStartHour()).isEqualTo(14);
        assertThat(profile.recommendedEndHour()).isEqualTo(16);
        assertThat(profile.sampleSize()).isEqualTo(5);
    }

    @Test
    void aprendeLaVentanaRealAunqueLaModulacionDigaOtraCosa() {
        // El local declara 09–12, pero rechaza en la mañana y recibe a las 17–18.
        List<DeliveryAttempt> attempts = new ArrayList<>();
        attempts.addAll(repeat(3, 9, REJECTED_NOT_HOME));
        attempts.addAll(repeat(2, 10, REJECTED_REFUSED));
        attempts.addAll(repeat(3, 17, ACCEPTED));
        attempts.addAll(repeat(3, 18, ACCEPTED));

        CustomerTimeWindowProfile profile = service.learn(customerId, attempts);

        assertThat(profile.recommendedStartHour()).isEqualTo(17);
        assertThat(profile.recommendedEndHour()).isEqualTo(19);
        assertThat(profile.hoursToAvoid()).containsExactly(9, 10);
    }

    @Test
    void unRechazoPesaMasQueUnaAceptacionEnLaMismaHora() {
        List<DeliveryAttempt> attempts = List.of(at(11, ACCEPTED), at(11, REJECTED_NOT_HOME), at(15, ACCEPTED));

        CustomerTimeWindowProfile profile = service.learn(customerId, attempts);

        assertThat(profile.hoursToAvoid()).containsExactly(11); // +1 − 1,5 < 0
        assertThat(profile.recommendedStartHour()).isIn(14, 15);
    }

    @Test
    void lasHorasFueraDelHorarioOperativoNoCuentanParaVentanaNiParaEvitar() {
        List<DeliveryAttempt> attempts = List.of(at(6, REJECTED_NOT_HOME), at(21, ACCEPTED), at(22, ACCEPTED));

        CustomerTimeWindowProfile profile = service.learn(customerId, attempts);

        assertThat(profile.recommendedStartHour()).isEqualTo(8); // ningún bloque 08–20 tiene puntaje
        assertThat(profile.hoursToAvoid()).isEmpty();
        assertThat(profile.sampleSize()).isEqualTo(3);
    }

    @Test
    void laConfianzaCreceConLosIntentosYSeSaturaEnCinco() {
        assertThat(service.learn(customerId, repeat(1, 12, ACCEPTED)).confidence()).isEqualTo(0.2);
        assertThat(service.learn(customerId, repeat(4, 12, ACCEPTED)).confidence()).isEqualTo(0.8);
        assertThat(service.learn(customerId, repeat(5, 12, ACCEPTED)).confidence()).isEqualTo(1.0);
        assertThat(service.learn(customerId, repeat(12, 12, ACCEPTED)).confidence()).isEqualTo(1.0);
    }

    @Test
    void usaLaHoraDeSantiagoConSuHorarioDeVerano() {
        // 17:00 UTC = 14:00 en diciembre (UTC−3) y 13:00 en julio (UTC−4).
        DeliveryAttempt summer = DeliveryAttempt.builder()
                .attemptedAt(Instant.parse("2026-12-01T17:00:00Z")).outcome(REJECTED_NOT_HOME).build();
        DeliveryAttempt winter = DeliveryAttempt.builder()
                .attemptedAt(Instant.parse("2026-07-01T17:00:00Z")).outcome(REJECTED_NOT_HOME).build();

        assertThat(service.learn(customerId, List.of(summer)).hoursToAvoid()).containsExactly(14);
        assertThat(service.learn(customerId, List.of(winter)).hoursToAvoid()).containsExactly(13);
    }

    @Test
    void leeLosIntentosDelLocalDesdeElRepositorio() {
        DeliveryAttemptRepository repository = mock(DeliveryAttemptRepository.class);
        when(repository.findByCustomerId(customerId)).thenReturn(repeat(5, 16, ACCEPTED));

        CustomerTimeWindowProfile profile = service.learn(customerId, repository);

        assertThat(profile.recommendedStartHour()).isIn(15, 16);
        assertThat(profile.confidence()).isEqualTo(1.0);
    }

    // ---- HU-11: histograma y "X de Y visitas" (mismo cálculo que la ventana) ----

    @Test
    void elHistogramaCuentaLasVisitasAceptadasYNoAceptadasPorHora() {
        List<DeliveryAttempt> attempts = new ArrayList<>();
        attempts.addAll(repeat(3, 9, REJECTED_NOT_HOME));
        attempts.addAll(repeat(2, 10, REJECTED_REFUSED));
        attempts.addAll(repeat(3, 17, ACCEPTED));
        attempts.add(at(17, AttemptOutcome.RESCHEDULED_BY_CUSTOMER));
        attempts.addAll(repeat(3, 18, ACCEPTED));

        CustomerTimeWindowProfile profile = service.learn(customerId, attempts);

        assertThat(profile.histogram()).hasSize(24);
        assertThat(profile.histogram()).extracting(TimeWindowLearningService.HourBucket::hour)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 24).boxed().toList());
        assertThat(profile.histogram().get(9)).isEqualTo(new TimeWindowLearningService.HourBucket(9, 0, 3));
        assertThat(profile.histogram().get(10)).isEqualTo(new TimeWindowLearningService.HourBucket(10, 0, 2));
        assertThat(profile.histogram().get(17)).isEqualTo(new TimeWindowLearningService.HourBucket(17, 3, 1));
        assertThat(profile.histogram().get(18)).isEqualTo(new TimeWindowLearningService.HourBucket(18, 3, 0));
        assertThat(profile.histogram().stream().mapToInt(TimeWindowLearningService.HourBucket::attempts).sum())
                .isEqualTo(profile.sampleSize());
    }

    @Test
    void cuentaXDeYVisitasDentroDeLaVentanaRecomendada() {
        List<DeliveryAttempt> attempts = new ArrayList<>();
        attempts.addAll(repeat(3, 9, REJECTED_NOT_HOME));
        attempts.addAll(repeat(5, 17, ACCEPTED));
        attempts.add(at(17, REJECTED_NOT_HOME));
        attempts.addAll(repeat(3, 18, ACCEPTED));
        attempts.add(at(19, ACCEPTED)); // fuera de la ventana 17–19

        CustomerTimeWindowProfile profile = service.learn(customerId, attempts);

        assertThat(profile.recommendedStartHour()).isEqualTo(17);
        assertThat(profile.recommendedEndHour()).isEqualTo(19);
        assertThat(profile.acceptedInWindow()).isEqualTo(8);  // 5 + 3
        assertThat(profile.attemptsInWindow()).isEqualTo(9);  // 6 + 3
        assertThat(profile.learned()).isTrue();
    }

    @Test
    void unaVentanaSinVisitasCuentaCeroDeCero() {
        // Solo rechazos: gana un bloque sin visitas (puntaje 0), que no tiene nada que contar.
        CustomerTimeWindowProfile profile = service.learn(customerId, repeat(4, 12, REJECTED_NOT_HOME));

        assertThat(profile.recommendedStartHour()).isEqualTo(8);
        assertThat(profile.acceptedInWindow()).isZero();
        assertThat(profile.attemptsInWindow()).isZero();
        assertThat(profile.histogram().get(12).rejected()).isEqualTo(4);
    }

    @Test
    void sinHistorialElHistogramaEstaVacioYNoEsAprendido() {
        CustomerTimeWindowProfile profile = service.learn(customerId, List.of());

        assertThat(profile.learned()).isFalse();
        assertThat(profile.histogram()).hasSize(24).allMatch(b -> b.attempts() == 0);
        assertThat(profile.acceptedInWindow()).isZero();
        assertThat(profile.attemptsInWindow()).isZero();
    }
}
