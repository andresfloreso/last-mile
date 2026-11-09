import type { StopView } from "@/lib/routeView";
import { etaHour } from "@/lib/routeView";
import { BANDS, BAND_CLASSES, DAY_END, DAY_START, axisPosition } from "@/lib/timeWindows";

const TICKS = [7, 9.5, 12, 14.5, 17, 19.5, 22];

function label(h: number) {
  const hh = Math.floor(h);
  return `${String(hh).padStart(2, "0")}:${h % 1 ? "30" : "00"}`;
}

interface Props {
  stops: StopView[];
  selectedId: string | null;
  onSelect: (shipmentId: string) => void;
}

/**
 * Línea de tiempo del día (HU-27): eje 07–22 con las 4 franjas como bandas suaves y una raya por
 * parada en su hora estimada. Las paradas fuera de ventana llevan un indicador de alerta.
 */
export default function DayTimeline({ stops, selectedId, onSelect }: Props) {
  const outside = stops.filter((s) => s.outsideWindow).length;
  const pct = (h: number) => `${axisPosition(h) * 100}%`;

  return (
    <section className="flex h-[110px] shrink-0 flex-col justify-between border-t border-line bg-panel px-4 py-3" aria-label="Línea de tiempo">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 className="text-13 font-semibold text-ink">Línea de tiempo · cada raya es la llegada a un local</h2>
        <p className="text-xs text-muted">
          Raya dentro de su banda = llega a tiempo ·{" "}
          <span className={outside > 0 ? "font-semibold text-reject" : undefined}>
            {outside} {outside === 1 ? "llegada" : "llegadas"} fuera de ventana
          </span>
        </p>
      </div>

      <div className="relative mx-2 h-8">
        {BANDS.map((b) => (
          <div
            key={b.id}
            className={`absolute top-2 h-4 rounded ${BAND_CLASSES[b.id].bg}`}
            style={{ left: pct(b.start), width: `calc(${pct(b.end)} - ${pct(b.start)} - 2px)` }}
            title={`${b.label}: ${String(b.start).padStart(2, "0")}–${b.end}`}
            aria-hidden="true"
          />
        ))}
        {stops.map((s) => {
          const h = etaHour(s);
          const sel = s.shipmentId === selectedId;
          const beyond = h > DAY_END || h < DAY_START;
          return (
            <button
              key={s.shipmentId}
              type="button"
              onClick={() => onSelect(s.shipmentId)}
              aria-label={`Parada ${s.position}, ${s.name}, llega ${s.eta}${s.outsideWindow ? ", fuera de ventana" : ""}`}
              aria-pressed={sel}
              className="group absolute top-0 flex h-8 w-3 -translate-x-1/2 justify-center focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              style={{ left: pct(h) }}
            >
              <span
                className={`block h-full rounded-sm ${sel ? "w-1 bg-ink" : `w-0.5 ${BAND_CLASSES[s.band.id].solid}`} group-hover:w-1`}
              />
              {(s.outsideWindow || beyond) && (
                <span
                  className="absolute -top-1.5 h-2 w-2 rotate-45 rounded-[1px] border border-panel bg-reject"
                  aria-hidden="true"
                />
              )}
            </button>
          );
        })}
      </div>

      <div className="relative mx-2 h-4 text-xs tabular-nums text-muted" aria-hidden="true">
        {TICKS.map((t, i) => (
          <span
            key={t}
            className="absolute"
            style={{
              left: pct(t),
              transform: i === 0 ? "none" : i === TICKS.length - 1 ? "translateX(-100%)" : "translateX(-50%)",
            }}
          >
            {label(t)}
          </span>
        ))}
      </div>
    </section>
  );
}
