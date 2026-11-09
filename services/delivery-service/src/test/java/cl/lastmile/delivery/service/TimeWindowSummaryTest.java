package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-11: la ventana real se explica con visitas, nunca con un puntaje. */
class TimeWindowSummaryTest {

    private final TimeWindowLearningService learning = new TimeWindowLearningService();

    private static List<DeliveryAttempt> repeat(int times, int hour, AttemptOutcome outcome) {
        List<DeliveryAttempt> list = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            list.add(DeliveryAttempt.builder()
                    .attemptedAt(LocalDate.of(2026, 12, 1).atTime(hour, 30).atZone(ZoneId.of("America/Santiago")).toInstant())
                    .outcome(outcome).build());
        }
        return list;
    }

    private String summary(List<DeliveryAttempt> attempts) {
        return TimeWindowSummary.of(learning.learn(UUID.randomUUID(), attempts));
    }

    @Test
    void conVisitasEnLaFranjaDiceCuantasRecibio() {
        // 12 h: 5 entregas; 13 h: 3 entregas y 2 rechazos; 11 h: 1 rechazo (así gana el bloque 12–14).
        List<DeliveryAttempt> attempts = new ArrayList<>(repeat(5, 12, AttemptOutcome.ACCEPTED));
        attempts.addAll(repeat(3, 13, AttemptOutcome.ACCEPTED));
        attempts.addAll(repeat(2, 13, AttemptOutcome.REJECTED_NOT_HOME));
        attempts.addAll(repeat(1, 11, AttemptOutcome.REJECTED_REFUSED));

        assertThat(summary(attempts)).isEqualTo("Recibió 8 de 10 visitas entre 12:00 y 14:00");
    }

    @Test
    void usaSingularConUnaSolaVisita() {
        // Una entrega a las 15 h: el primer bloque que la incluye es 14–16.
        assertThat(summary(repeat(1, 15, AttemptOutcome.ACCEPTED))).isEqualTo("Recibió 1 de 1 visita entre 14:00 y 16:00");
    }

    @Test
    void sinIntentosExplicaQueSeUsaElHorarioPorDefecto() {
        assertThat(summary(List.of()))
                .isEqualTo("Sin intentos de entrega: la ruta usa el horario comercial por defecto, entre 09:00 y 18:00");
    }

    @Test
    void unaFranjaSinVisitasNoInventaUnConteo() {
        assertThat(summary(repeat(4, 12, AttemptOutcome.REJECTED_NOT_HOME)))
                .isEqualTo("Aún no hay visitas entre 08:00 y 10:00; en las horas visitadas predominan los rechazos");
    }
}
