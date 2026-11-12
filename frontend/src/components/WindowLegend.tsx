import { BANDS, BAND_CLASSES } from "@/lib/timeWindows";

/** Horario de manejo de cada color de la línea (HU-15): los mismos tokens que las franjas. */
const DRIVE_LABEL: Record<string, string> = {
  morning: "07:00–11:59",
  midday: "12:00–14:59",
  afternoon: "15:00–18:59",
  night: "19:00 en adelante",
};

/**
 * Leyenda fija del mapa: colores por franja de la ventana real, local nuevo y fuera de ventana; y la
 * línea, del color de la hora a la que el camión maneja cada tramo (HU-15).
 */
export default function WindowLegend({ showGhost = false }: { showGhost?: boolean }) {
  return (
    <div className="space-y-1 rounded-lg border border-line bg-float px-3 py-2 text-xs text-muted" aria-label="Leyenda del mapa">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="font-semibold text-ink">Ventana real</span>
        {BANDS.map((b) => (
          <span key={b.id} className="flex items-center gap-1.5">
            <span className={`h-2.5 w-2.5 rounded-full ${BAND_CLASSES[b.id].solid}`} aria-hidden="true" />
            {b.label}
          </span>
        ))}
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full border border-dashed border-muted" aria-hidden="true" />
          Local nuevo
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-full border-2 border-reject" aria-hidden="true" />
          Fuera de ventana
        </span>
      </div>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="font-semibold text-ink">El camión maneja</span>
        {BANDS.map((b) => (
          <span key={b.id} className="flex items-center gap-1.5">
            <span className={`h-1 w-4 rounded-full ${BAND_CLASSES[b.id].solid}`} aria-hidden="true" />
            {DRIVE_LABEL[b.id]}
          </span>
        ))}
        {showGhost && (
          <span className="flex items-center gap-1.5">
            <span className="w-4 border-t-2 border-dashed border-muted" aria-hidden="true" />
            Antes de tus cambios
          </span>
        )}
      </div>
    </div>
  );
}
