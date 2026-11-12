package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 *
 * HU-12: cada intento pesa según su antigüedad (decaimiento con vida media configurable), para que el
 * local que cambió de hábito se aprenda sin esperar a que se acumulen intentos nuevos; y el perfil se
 * guarda en una caché ({@link TimeWindowProfileCache}, Redis con TTL) que se invalida al registrar un
 * intento. Los conteos del histograma ("recibió X de Y visitas", HU-11) siguen siendo visitas reales,
 * sin ponderar: el decaimiento solo cambia qué ventana se elige.
 */
@Service
public class TimeWindowLearningService {

    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final double ACCEPT_WEIGHT = 1.0;
    private static final double REJECT_WEIGHT = -1.5; // rechazar pesa más que aceptar, para evitar esas horas
    private static final double SECONDS_PER_DAY = 86_400.0;

    /** Vida media del peso de un intento, en días; 0 o menos = sin decaimiento (todos pesan igual). */
    private final double halfLifeDays;
    private final Clock clock;
    private final TimeWindowProfileCache cache;

    /** Sin decaimiento ni caché: el comportamiento de HU-04/HU-11 (lo usan los tests unitarios). */
    public TimeWindowLearningService() {
        this(0, Clock.systemUTC(), TimeWindowProfileCache.NONE);
    }

    @Autowired
    public TimeWindowLearningService(@Value("${time-window.decay.half-life-days:90}") double halfLifeDays,
                                     TimeWindowProfileCache cache) {
        this(halfLifeDays, Clock.systemUTC(), cache);
    }

    public TimeWindowLearningService(double halfLifeDays, Clock clock, TimeWindowProfileCache cache) {
        this.halfLifeDays = halfLifeDays;
        this.clock = clock;
        this.cache = cache;
    }

    /**
     * Perfil del local: de la caché si está, o calculado con sus intentos (y guardado en la caché).
     */
    public CustomerTimeWindowProfile learn(UUID customerId, DeliveryAttemptRepository repository) {
        return cache.get(customerId).orElseGet(() -> {
            CustomerTimeWindowProfile profile = learn(customerId, repository.findByCustomerId(customerId));
            cache.put(profile);
            return profile;
        });
    }

    /** Invalida el perfil guardado del local (HU-12: al registrar un intento de entrega). */
    public void forget(UUID customerId) {
        cache.evict(customerId);
    }

    /**
     * Peso de un intento según su antigüedad: 1 si es de hoy, 0,5 a una vida media, 0,25 a dos. Un intento
     * con fecha futura pesa 1.
     */
    double weight(Instant attemptedAt, Instant now) {
        if (halfLifeDays <= 0) return 1.0;
        double ageDays = Math.max(0, Duration.between(attemptedAt, now).getSeconds() / SECONDS_PER_DAY);
        return Math.pow(0.5, ageDays / halfLifeDays);
    }

    public CustomerTimeWindowProfile learn(UUID customerId, List<DeliveryAttempt> attempts) {
        Instant now = clock.instant();
        double[] hourScore = new double[24];
        int[] hourSamples = new int[24];
        int[] hourAccepted = new int[24];

        for (DeliveryAttempt attempt : attempts) {
            int hour = ZonedDateTime.ofInstant(attempt.getAttemptedAt(), ZONE).getHour();
            hourSamples[hour]++;
            boolean accepted = attempt.getOutcome() == AttemptOutcome.ACCEPTED;
            if (accepted) hourAccepted[hour]++;
            hourScore[hour] += (accepted ? ACCEPT_WEIGHT : REJECT_WEIGHT) * weight(attempt.getAttemptedAt(), now);
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

        // HU-11: la explicación de la ventana sale de los mismos conteos con los que se eligió.
        List<HourBucket> histogram = IntStream.range(0, 24)
                .mapToObj(h -> new HourBucket(h, hourAccepted[h], hourSamples[h] - hourAccepted[h]))
                .toList();
        int windowAttempts = 0;
        int windowAccepted = 0;
        for (int h = bestStartHour; h < bestStartHour + 2; h++) {
            windowAttempts += hourSamples[h];
            windowAccepted += hourAccepted[h];
        }

        return new CustomerTimeWindowProfile(
                customerId,
                bestStartHour,
                bestStartHour + 2,
                avoidHours,
                attempts.size(),
                confidence,
                histogram,
                windowAccepted,
                windowAttempts
        );
    }

    /**
     * Perfil horario de un local. Es la única fuente de la ventana real: la ruta
     * (RouteOptimizationService) y la pantalla del local (HU-11) leen este mismo objeto.
     *
     * @param histogram          24 casilleros (0 a 23 h, hora de Santiago) con las visitas aceptadas y no aceptadas
     * @param acceptedInWindow   visitas aceptadas dentro de la ventana recomendada (la "X" de "X de Y visitas")
     * @param attemptsInWindow   visitas dentro de la ventana recomendada (la "Y")
     */
    public record CustomerTimeWindowProfile(
            UUID customerId,
            int recommendedStartHour,
            int recommendedEndHour,
            List<Integer> hoursToAvoid,
            int sampleSize,
            double confidence,
            List<HourBucket> histogram,
            int acceptedInWindow,
            int attemptsInWindow
    ) {
        static CustomerTimeWindowProfile unknown(UUID customerId) {
            // Sin historial: se asume ventana comercial estándar y confianza cero.
            List<HourBucket> empty = IntStream.range(0, 24).mapToObj(h -> new HourBucket(h, 0, 0)).toList();
            return new CustomerTimeWindowProfile(customerId, 9, 18, List.of(), 0, 0.0, empty, 0, 0);
        }

        /** true si la ventana sale de intentos reales; false si es la ventana comercial por defecto. */
        public boolean learned() {
            return sampleSize > 0;
        }
    }

    /**
     * Visitas de un local en una hora del día.
     *
     * @param rejected intentos no aceptados: local cerrado, rechazo o pidió otro horario
     */
    public record HourBucket(int hour, int accepted, int rejected) {
        public int attempts() {
            return accepted + rejected;
        }
    }
}
