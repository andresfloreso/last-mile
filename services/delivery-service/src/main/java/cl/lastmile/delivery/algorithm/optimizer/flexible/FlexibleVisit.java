package cl.lastmile.delivery.algorithm.optimizer.flexible;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.UUID;

/**
 * Una parada para el prototipo de HU-37: dato fijo del problema.
 *
 * @param point          índice del local en la tabla de tiempos; -1 si no tiene coordenadas
 * @param curve          probabilidad de que reciba, hora por hora
 * @param windowStartSec apertura de la ventana real aprendida (solo para medir la ruta de hoy, que espera a que abra)
 * @param windowEndSec   cierre de la ventana real aprendida
 * @param preference     FIRST, AUTO o LAST
 */
public record FlexibleVisit(@PlanningId UUID id, int point, AcceptanceCurve curve, int windowStartSec, int windowEndSec,
                            String preference, double weightKg, double volumeM3) {

    public boolean first() {
        return "FIRST".equals(preference);
    }

    public boolean last() {
        return "LAST".equals(preference);
    }
}
