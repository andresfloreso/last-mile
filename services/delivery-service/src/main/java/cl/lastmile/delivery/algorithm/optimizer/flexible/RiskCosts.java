package cl.lastmile.delivery.algorithm.optimizer.flexible;

/**
 * Lo que cuesta cada cosa en el objetivo del prototipo de HU-37, en pesos. Son <b>supuestos para
 * conversar con logística</b>, no costos medidos: se cambian en {@code routing.optimizer.risk.*}.
 *
 * @param failedDelivery      una entrega que no se concreta en primera visita (volver otro día, el local sin producto)
 * @param perKm               un km recorrido (combustible y desgaste)
 * @param perShiftMinute      un minuto de jornada, desde que sale de bodega hasta que vuelve (conductor y peoneta)
 * @param perWaitMinute       recargo por minuto esperando frente a un local, además de la jornada
 * @param maxWaitMinutes      lo máximo que el camión espera en un local a que suba la probabilidad
 * @param maxDepartureDelayMinutes cuánto puede atrasar la salida de bodega para no llegar a esperar a la primera parada
 */
public record RiskCosts(long failedDelivery, long perKm, long perShiftMinute, long perWaitMinute,
                        int maxWaitMinutes, int maxDepartureDelayMinutes) {

    public static final RiskCosts DEFAULT = new RiskCosts(60_000, 700, 250, 50, 120, 180);
}
