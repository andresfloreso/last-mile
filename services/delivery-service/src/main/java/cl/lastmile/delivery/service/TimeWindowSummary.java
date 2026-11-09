package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;

/**
 * Explica la ventana real en una frase, siempre con conteos de visitas y nunca con un
 * puntaje (HU-11): "Recibió 8 de 10 visitas entre 12:00 y 14:00".
 */
public final class TimeWindowSummary {

    private TimeWindowSummary() {
    }

    public static String of(CustomerTimeWindowProfile p) {
        String range = "entre %s y %s".formatted(hhmm(p.recommendedStartHour()), hhmm(p.recommendedEndHour()));
        if (!p.learned()) {
            return "Sin intentos de entrega: la ruta usa el horario comercial por defecto, " + range;
        }
        if (p.attemptsInWindow() == 0) {
            // La franja elegida no tiene visitas: en todas las horas visitadas hubo más rechazos que entregas.
            return "Aún no hay visitas " + range + "; en las horas visitadas predominan los rechazos";
        }
        return "Recibió %d de %d %s %s".formatted(
                p.acceptedInWindow(), p.attemptsInWindow(), p.attemptsInWindow() == 1 ? "visita" : "visitas", range);
    }

    private static String hhmm(int hour) {
        return "%02d:00".formatted(hour);
    }
}
