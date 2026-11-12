import type { StopView } from "@/lib/routeView";
import type { RouteEditor } from "@/hooks/useRouteEditor";
import type { RouteSummaryDto } from "@/types";
import { countDelta, kmDelta, timeDelta } from "@/lib/routeEdit";
import { longDate } from "@/lib/timeWindows";
import { DEPARTURE_TIME } from "@/lib/config";
import TruckColumn from "@/components/TruckColumn";

interface Props {
  date: string;
  editor: RouteEditor;
  /** Paradas en el orden que se muestra: el provisional mientras se arrastra. */
  stops: StopView[];
  selectedId: string | null;
  onSelect: (shipmentId: string) => void;
}

function Kpi({ label, value, delta, worse }: { label: string; value: string; delta: string; worse: boolean }) {
  return (
    <div className="rounded-lg bg-tile px-3 py-2.5">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="text-xl font-semibold tabular-nums text-ink">{value}</dd>
      <dd className={`text-xs ${worse ? "font-semibold text-reject" : "text-muted"}`}>{delta}</dd>
    </div>
  );
}

function km(meters: number): string {
  return `${(meters / 1000).toLocaleString("es-CL", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} km`;
}

/** Indicadores con la diferencia frente a la ruta del sistema (HU-15). */
function EditKpis({ now, base }: { now: RouteSummaryDto; base: RouteSummaryDto }) {
  const back = now.returnTime ? `${now.returnTime}${now.returnDayOffset > 0 ? " +1" : ""}` : "—";
  const lateBy = (now.returnDayOffset - base.returnDayOffset) * 1440 + minutes(now.returnTime) - minutes(base.returnTime);
  return (
    <dl className="grid grid-cols-3 gap-2" aria-label="Indicadores de la ruta">
      <Kpi label="Recorrido" value={km(now.distanceMeters)} delta={kmDelta(now.distanceMeters, base.distanceMeters)} worse={now.distanceMeters - base.distanceMeters >= 50} />
      <Kpi
        label="Vuelve a bodega"
        value={back}
        delta={timeDelta(now.returnTime, now.returnDayOffset, base.returnTime, base.returnDayOffset)}
        worse={lateBy > 0}
      />
      <Kpi
        label="Fuera de ventana"
        value={String(now.outsideWindowStops)}
        delta={countDelta(now.outsideWindowStops, base.outsideWindowStops)}
        worse={now.outsideWindowStops > base.outsideWindowStops}
      />
    </dl>
  );
}

function minutes(time: string | null): number {
  if (!time) return 0;
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
}

/**
 * Armado manual de la ruta (HU-15): indicadores frente a la ruta del sistema, las paradas de cada camión
 * para arrastrar y fijar, y Deshacer / Reoptimizar el resto / Guardar ruta.
 *
 * Las columnas van en una fila con scroll horizontal: hoy hay un camión; HU-16 agrega una por camión.
 */
export default function RouteEditorPanel({ date, editor, stops, selectedId, onSelect }: Props) {
  const { current, baseline, busy } = editor;
  return (
    <aside className="flex h-full w-full flex-col bg-panel" aria-label="Armado manual de la ruta">
      {/* Los indicadores quedan fijos arriba: se ven mientras se mueve una parada al fondo de la lista. */}
      <div className="shrink-0 space-y-3 px-4 pb-3 pt-4">
        <div>
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">Armado manual · Salida {DEPARTURE_TIME}</p>
            <span
              className={`shrink-0 whitespace-nowrap rounded-md px-2 py-0.5 text-xs font-semibold ${editor.dirty ? "bg-warning-bg text-warning-ink" : "bg-tile text-muted"}`}
              role="status"
            >
              {editor.dirty ? "Cambios sin guardar" : "Sin cambios"}
            </span>
          </div>
          <h1 className="text-22 font-bold text-ink">{longDate(date)}</h1>
        </div>

        {current && baseline && <EditKpis now={current.summary} base={baseline.summary} />}

        <p className="text-xs text-muted">
          Arrastra una parada (o Alt + ↑ / ↓) para cambiar el orden; al soltar se recalculan las horas. El candado la
          deja fija al reoptimizar.
        </p>
        {busy === "preview" && <p className="text-xs text-muted" role="status">Recalculando horas…</p>}
        {busy === "reoptimize" && <p className="text-xs text-muted" role="status">Reoptimizando las paradas sin candado…</p>}
        {current?.optimizerNote && <p className="rounded-lg bg-warning-bg px-3 py-2 text-xs text-warning-ink">{current.optimizerNote}</p>}
        {editor.error && (
          <p className="rounded-lg bg-warning-bg px-3 py-2 text-13 text-warning-ink" role="alert">
            {editor.error}
          </p>
        )}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto border-t border-line">
        <div className="flex gap-2 overflow-x-auto pt-3">
          <TruckColumn
            title="Camión 1"
            subtitle={current ? `${current.stops.length} paradas` : undefined}
            stops={stops}
            order={editor.order}
            pinned={editor.pinned}
            busy={busy != null}
            selectedId={selectedId}
            onSelect={onSelect}
            onDrag={editor.drag}
            onMove={editor.move}
            onDragCancel={editor.cancelDrag}
            onTogglePin={editor.togglePin}
          />
        </div>
      </div>

      <div className="flex shrink-0 flex-wrap gap-2 border-t border-line bg-panel p-3">
        <button
          type="button"
          onClick={editor.undo}
          disabled={!editor.canUndo || busy === "save"}
          className="h-11 rounded-lg border border-border px-3 text-sm font-semibold text-ink hover:bg-tile disabled:opacity-50"
        >
          Deshacer
        </button>
        <button
          type="button"
          onClick={editor.reoptimizeRest}
          disabled={busy != null}
          className="h-11 flex-1 rounded-lg border border-border px-3 text-sm font-semibold text-ink hover:bg-tile disabled:opacity-50"
        >
          {busy === "reoptimize" ? "Reoptimizando…" : "Reoptimizar el resto"}
        </button>
        <button
          type="button"
          onClick={editor.save}
          disabled={busy != null || !editor.dirty}
          className="h-11 flex-1 rounded-lg bg-primary px-3 text-sm font-semibold text-primary-ink disabled:opacity-50"
        >
          {busy === "save" ? "Guardando…" : "Guardar ruta"}
        </button>
        <button
          type="button"
          onClick={editor.exit}
          disabled={busy === "save"}
          className="h-9 w-full rounded-lg text-13 font-semibold text-muted hover:bg-tile disabled:opacity-50"
        >
          {editor.dirty ? "Salir sin guardar" : "Salir"}
        </button>
      </div>
    </aside>
  );
}
