package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Idea tomada de la conversación de referencia: "el algoritmo aprendía la
 * ventana horaria donde los clientes recibían y en qué horarios rechazaban".
 *
 * Implementación: para cada cliente se arma un histograma de 24 casilleros
 * (uno por hora del día). Cada intento ACCEPTED suma +1 al casillero de esa
 * hora; cada intento rechazado resta peso. Al final se calcula un "score"
 * por hora y se determina la ventana horaria de mejor desempeño (bloque de
 * horas contiguas con score más alto), más las horas a evitar.
 */
@Service
@RequiredArgsConstructor
public class TimeWindowLearningService {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final double ACCEPT_WEIGHT = 1.0;
    private static final double REJECT_WEIGHT = -1.5; // rechazar pesa más que aceptar, para evitar esas horas

    public CustomerTimeWindowProfile learn(UUID customerId, DeliveryAttemptRepository repository) {
        List<DeliveryAttempt> attempts = repository.findByCustomerId(customerId);
        return learn(customerId, attempts);
    }

    public CustomerTimeWindowProfile learn(UUID customerId, List<DeliveryAttempt> attempts) {
        double[] hourScore = new double[24];
        int[] hourSamples = new int[24];

        for (DeliveryAttempt attempt : attempts) {
            int hour = ZonedDateTime.ofInstant(attempt.getAttemptedAt(), ZONE).getHour();
            hourSamples[hour]++;
            hourScore[hour] += attempt.getOutcome() == AttemptOutcome.ACCEPTED ? ACCEPT_WEIGHT : REJECT_WEIGHT;
        }

        if (attempts.isEmpty()) {
            return CustomerTimeWindowProfile.unknown(customerId);
        }

        // Ventana recomendada: bloque contiguo de 2 horas con score acumulado máximo,
        // sólo considerando horas dentro del horario operativo (08:00 - 20:00).
        int bestStartHour = 8;
        double bestBlockScore = Double.NEGATIVE_INFINITY;
        for (int start = 8; start <= 18; start++) {
            double blockScore = hourScore[start] + hourScore[start + 1];
            if (blockScore > bestBlockScore) {
                bestBlockScore = blockScore;
                bestStartHour = start;
            }
        }

        List<Integer> avoidHours = IntStream.range(8, 20)
                .filter(h -> hourSamples[h] > 0 && hourScore[h] < 0)
                .boxed()
                .collect(Collectors.toList());

        double confidence = Math.min(1.0, attempts.size() / 5.0); // 5+ intentos => confianza máxima

        return new CustomerTimeWindowProfile(
                customerId,
                bestStartHour,
                bestStartHour + 2,
                avoidHours,
                attempts.size(),
                confidence
        );
    }

    public record CustomerTimeWindowProfile(
            UUID customerId,
            int recommendedStartHour,
            int recommendedEndHour,
            List<Integer> hoursToAvoid,
            int sampleSize,
            double confidence
    ) {
        static CustomerTimeWindowProfile unknown(UUID customerId) {
            // Sin historial: se asume ventana comercial estándar y confianza cero.
            return new CustomerTimeWindowProfile(customerId, 9, 18, List.of(), 0, 0.0);
        }
    }
}
