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
 * @param serviceSec     tiempo de descarga en la parada (HU-15); menos de 0 usa el del camión
 */
public record Visit(@PlanningId UUID id, int point, int windowStartSec, int windowEndSec,
                   String preference, double weightKg, double volumeM3, int serviceSec) {

    /** Visita con el tiempo de descarga del camión ({@code serviceSec} = -1). */
    public Visit(UUID id, int point, int windowStartSec, int windowEndSec,
                 String preference, double weightKg, double volumeM3) {
        this(id, point, windowStartSec, windowEndSec, preference, weightKg, volumeM3, -1);
    }

    public boolean first() {
        return "FIRST".equals(preference);
    }

    public boolean last() {
        return "LAST".equals(preference);
    }
}
