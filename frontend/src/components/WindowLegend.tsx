import { BANDS, BAND_CLASSES } from "@/lib/timeWindows";

/** Leyenda fija del mapa: colores por franja de la ventana real, local nuevo y fuera de ventana. */
export default function WindowLegend() {
  return (
    <div
      className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-line bg-float px-3 py-2 text-xs text-muted"
      aria-label="Leyenda del mapa"
    >
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
  );
}
