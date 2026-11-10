package cl.lastmile.delivery.algorithm.optimizer;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.UUID;

/**
 * Una parada para el solver (HU-28): dato fijo del problema, no se modifica al resolver.
 *
 * @param point          índice del local en {@link TravelTimes}; -1 si no tiene coordenadas
 *                       (no suma viaje y el camión sigue en el punto anterior)
 * @param windowStartSec apertura de la ventana real, en segundos desde la medianoche
 * @param windowEndSec   cierre de la ventana real
 * @param preference     FIRST, AUTO o LAST
 */
public record Visit(@PlanningId UUID id, int point, int windowStartSec, int windowEndSec,
                   String preference, double weightKg, double volumeM3) {

    public boolean first() {
        return "FIRST".equals(preference);
    }

    public boolean last() {
        return "LAST".equals(preference);
    }
}
