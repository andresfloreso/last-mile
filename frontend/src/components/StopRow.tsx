import { forwardRef } from "react";
import type { StopView } from "@/lib/routeView";
import { BAND_CLASSES, declaredText, shortRange } from "@/lib/timeWindows";
import { ClockIcon, PinIcon } from "@/components/icons";

interface Props {
  stop: StopView;
  selected: boolean;
  focused: boolean;
  onSelect: () => void;
}

/** Badge circular de 28 px con el número de la parada, del color de su franja. */
export function StopBadge({ stop, size = 28 }: { stop: StopView; size?: number }) {
  const c = BAND_CLASSES[stop.band.id];
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-full text-xs font-bold tabular-nums ${
        stop.isNew ? `border-2 border-dashed ${c.border} ${c.bg} ${c.text}` : `${c.solid} text-badge-ink`
      }`}
      style={{ width: size, height: size }}
      aria-hidden="true"
    >
      {stop.position}
    </span>
  );
}

/** Una parada de la lista (HU-27): nombre, hora estimada, ventana real y avisos, sin depender solo del color. */
const StopRow = forwardRef<HTMLLIElement, Props>(function StopRow({ stop, selected, focused, onSelect }, ref) {
  const c = BAND_CLASSES[stop.band.id];
  return (
    <li
      ref={ref}
      id={`stop-${stop.shipmentId}`}
      role="option"
      aria-selected={selected}
      tabIndex={focused ? 0 : -1}
      onClick={onSelect}
      className={`flex cursor-pointer gap-3 rounded-lg border px-3 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-primary ${
        selected ? `bg-tile ${c.border}` : "border-transparent hover:bg-tile"
      }`}
    >
      <StopBadge stop={stop} />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <p className="truncate text-sm font-semibold text-ink">{stop.name}</p>
          <p className="shrink-0 text-sm font-semibold tabular-nums text-ink">
            {stop.eta}
            {stop.dayOffset > 0 && <span className="ml-1 text-xs font-medium text-reject">+1 día</span>}
          </p>
        </div>
        <div className="flex items-baseline justify-between gap-2">
          <p className="truncate text-xs text-muted">
            {stop.address}
            {stop.commune ? `, ${stop.commune}` : ""}
          </p>
          {stop.waitMinutes > 0 && <p className="shrink-0 text-xs tabular-nums text-muted">espera {stop.waitMinutes} min</p>}
        </div>

        <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs">
          {stop.isNew ? (
            <>
              <span className={`rounded px-1.5 py-0.5 font-semibold ${c.bg} ${c.text}`}>
                {stop.declared ? `Según modulación ${declaredText(stop.declared)}` : "Sin historial"}
              </span>
              <span className="text-muted">local nuevo</span>
            </>
          ) : (
            <>
              <span className={`rounded px-1.5 py-0.5 font-semibold ${c.bg} ${c.text}`}>
                Recibe {shortRange(stop.windowStart, stop.windowEnd)}
              </span>
              {stop.visits != null && (
                <span className="tabular-nums text-muted">
                  {stop.accepted} de {stop.visits}
                </span>
              )}
              {stop.declaredDiffers && stop.declared && (
                <span className="rounded border border-dashed border-border px-1.5 py-0.5 text-muted">
                  Declara {declaredText(stop.declared)}
                </span>
              )}
            </>
          )}
          {stop.outsideWindow && (
            <span className="flex items-center gap-1 font-semibold text-reject">
              <ClockIcon className="h-3.5 w-3.5" />
              Fuera de ventana
            </span>
          )}
          {stop.needsReview && (
            <span className="flex items-center gap-1 rounded bg-warning-bg px-1.5 py-0.5 font-semibold text-warning-ink">
              <PinIcon className="h-3.5 w-3.5" />
              Revisar ubicación
            </span>
          )}
        </div>
      </div>
    </li>
  );
});

export default StopRow;
