import { forwardRef, useState } from "react";
import { Link } from "react-router-dom";
import type { TimeWindow } from "@/types";
import type { StopView } from "@/lib/routeView";
import { BUSINESS_LABEL } from "@/lib/routeView";
import { BAND_CLASSES, DAY_END, DAY_START, bandVars, declaredSpans, longRange } from "@/lib/timeWindows";
import DeclaredWindowEditor from "@/components/DeclaredWindowEditor";
import type { DeclaredWindowPayload } from "@/lib/declaredWindow";
import { CloseIcon, PinIcon } from "@/components/icons";

/**
 * Histograma compacto de 07 a 22 h (datos de /time-window, HU-11): banda gris = modulación,
 * barras del color de la franja = entregas, barras del color de rechazo = no entregas.
 */
function MiniHistogram({ tw, stop }: { tw: TimeWindow; stop: StopView }) {
  const W = 288;
  const H = 64;
  const hours = DAY_END - DAY_START + 1; // 07..22, 16 columnas
  const col = W / hours;
  const buckets = tw.histogram.filter((b) => b.hour >= DAY_START && b.hour <= DAY_END);
  const max = Math.max(1, ...buckets.map((b) => Math.max(b.accepted, b.rejected)));
  const x = (h: number) => (Math.min(Math.max(h, DAY_START), DAY_END + 1) - DAY_START) * col;
  const v = bandVars(stop.band.id);
  const bar = col / 2 - 1;

  return (
    <figure className="space-y-1">
      <svg
        viewBox={`0 0 ${W} ${H + 14}`}
        className="h-auto w-full"
        role="img"
        aria-label={`Visitas por hora entre las 07 y las 22: ${tw.summary}`}
      >
        {declaredSpans(tw.declaredWindow).map(([a, b], i) => (
          <rect key={i} x={x(a)} y={0} width={x(b) - x(a)} height={H} fill="var(--color-modband)" rx={2} />
        ))}
        <line x1={0} x2={W} y1={H} y2={H} stroke="var(--color-border)" />
        {buckets.map((b) => {
          const cx = x(b.hour);
          const ha = (b.accepted / max) * (H - 4);
          const hr = (b.rejected / max) * (H - 4);
          return (
            <g key={b.hour}>
              <title>{`${String(b.hour).padStart(2, "0")}:00 · recibió ${b.accepted} · no recibió ${b.rejected}`}</title>
              {b.accepted > 0 && <rect x={cx + 1} y={H - ha} width={bar} height={ha} rx={1.5} fill={v.solid} />}
              {b.rejected > 0 && <rect x={cx + 1 + bar + 1} y={H - hr} width={bar} height={hr} rx={1.5} fill="var(--color-reject)" />}
            </g>
          );
        })}
        {[7, 12, 17, 22].map((h) => (
          <text key={h} x={x(h) + (h === 22 ? col : 0)} y={H + 12} fontSize="9" fill="var(--color-muted)" textAnchor={h === 22 ? "end" : "start"}>
            {String(h).padStart(2, "0")}
          </text>
        ))}
      </svg>
      <figcaption className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted">
        <span className="flex items-center gap-1">
          <span className="h-2 w-2.5 rounded-sm bg-modband" aria-hidden="true" /> Modulación
        </span>
        <span className="flex items-center gap-1">
          <span className={`h-2 w-2.5 rounded-sm ${BAND_CLASSES[stop.band.id].solid}`} aria-hidden="true" /> Recibió
        </span>
        <span className="flex items-center gap-1">
          <span className="h-2 w-2.5 rounded-sm bg-reject" aria-hidden="true" /> No recibió
        </span>
      </figcaption>
    </figure>
  );
}

interface Props {
  stop: StopView;
  timeWindow: TimeWindow | undefined;
  relocating: boolean;
  onClose: () => void;
  onSaveDeclared: (payload: DeclaredWindowPayload) => Promise<void>;
  onStartRelocate: () => void;
}

/** Tarjeta de detalle del local seleccionado (HU-27): modulación vs. ventana real, histograma y acciones. */
const StopDetailCard = forwardRef<HTMLDivElement, Props>(function StopDetailCard(props, ref) {
  const { stop, timeWindow: tw, relocating, onClose, onSaveDeclared, onStartRelocate } = props;
  const [editing, setEditing] = useState(false);
  const c = BAND_CLASSES[stop.band.id];
  const declared = tw?.declaredWindow ?? stop.declared;

  return (
    <section
      ref={ref}
      aria-label={`Detalle de ${stop.name}`}
      className="w-[320px] space-y-3 rounded-xl border border-line bg-float p-4 text-ink shadow-sm"
    >
      <header className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="truncate text-15 font-semibold">
            {stop.position} · {stop.name}
          </h2>
          <p className="truncate text-xs text-muted">
            {stop.address}
            {stop.businessType ? ` · ${BUSINESS_LABEL[stop.businessType]}` : ""}
          </p>
        </div>
        <div className="flex shrink-0 items-start gap-1">
          <div className="text-right">
            <p className="text-13 font-semibold tabular-nums">Llega {stop.eta}</p>
            {stop.waitMinutes > 0 && <p className="text-xs tabular-nums text-muted">espera {stop.waitMinutes} min</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar detalle"
            className="-mr-1 -mt-1 rounded p-1 text-muted hover:bg-tile hover:text-ink"
          >
            <CloseIcon />
          </button>
        </div>
      </header>

      {stop.outsideWindow && (
        <p className="text-xs font-semibold text-reject">Llega fuera de su ventana real</p>
      )}

      <div className="grid grid-cols-2 gap-2">
        <div className="rounded-lg bg-tile p-2.5">
          <div className="flex items-center justify-between">
            <p className="text-xs text-muted">Declara (modulación)</p>
            {!editing && (
              <button
                type="button"
                onClick={() => setEditing(true)}
                className="text-xs font-semibold text-ink underline underline-offset-2"
                aria-label="Editar modulación"
              >
                Editar
              </button>
            )}
          </div>
          <p className="text-13 font-semibold tabular-nums">
            {declared ? longRange(declared.start, declared.end) : "Sin modulación"}
          </p>
          {declared?.start2 && declared.end2 && (
            <p className="text-13 font-semibold tabular-nums">{longRange(declared.start2, declared.end2)}</p>
          )}
        </div>
        <div className={`rounded-lg p-2.5 ${c.bg}`}>
          <p className={`text-xs ${c.text}`}>{stop.isNew ? "Sin historial (por defecto)" : "Recibe de verdad"}</p>
          <p className={`text-13 font-semibold tabular-nums ${c.text}`}>{longRange(stop.windowStart, stop.windowEnd)}</p>
        </div>
      </div>

      {editing && (
        <DeclaredWindowEditor
          value={declared ?? null}
          onCancel={() => setEditing(false)}
          onSave={async (payload) => {
            await onSaveDeclared(payload);
            setEditing(false);
          }}
        />
      )}

      {tw ? (
        <>
          <p className="text-13">
            {tw.realWindow.basis === "DEFAULT" ? (
              "Sin intentos de entrega todavía: la ruta usa el horario comercial por defecto."
            ) : tw.realWindow.visits === 0 ? (
              tw.summary
            ) : (
              <>
                Recibió{" "}
                <strong className="font-semibold">
                  {tw.realWindow.acceptedVisits} de {tw.realWindow.visits}
                </strong>{" "}
                visitas entre {tw.realWindow.start.substring(0, 5)} y {tw.realWindow.end.substring(0, 5)}
              </>
            )}
          </p>
          <MiniHistogram tw={tw} stop={stop} />
        </>
      ) : (
        <div className="h-24 animate-pulse rounded-lg bg-tile" aria-label="Cargando visitas" />
      )}

      {stop.needsReview && (
        <div className="space-y-2 rounded-lg bg-warning-bg p-2.5 text-warning-ink">
          <p className="flex items-center gap-1.5 text-xs font-semibold">
            <PinIcon className="h-3.5 w-3.5" />
            Ubicación aproximada
          </p>
          {relocating ? (
            <p className="text-xs">Arrastra el pin del local en el mapa a su ubicación correcta.</p>
          ) : (
            <button
              type="button"
              onClick={onStartRelocate}
              className="h-8 rounded-md border border-current px-3 text-xs font-semibold"
            >
              Corregir ubicación
            </button>
          )}
        </div>
      )}

      <div className="grid grid-cols-2 gap-2">
        <span title="Disponible en HU-15">
          <button
            type="button"
            disabled
            className="h-9 w-full cursor-not-allowed rounded-lg border border-border text-13 font-semibold text-ink opacity-60"
          >
            Fijar posición
          </button>
        </span>
        <Link
          to={`/locales/${stop.customerId}`}
          className="flex h-9 items-center justify-center rounded-lg border border-border text-13 font-semibold text-ink hover:bg-tile"
        >
          Ver visitas
        </Link>
      </div>
    </section>
  );
});

export default StopDetailCard;
