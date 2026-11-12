import { useState } from "react";
import type { StopView } from "@/lib/routeView";
import { BAND_CLASSES, shortRange } from "@/lib/timeWindows";
import { StopBadge } from "@/components/StopRow";
import { ClockIcon, GripIcon, LockIcon, UnlockIcon } from "@/components/icons";

interface Props {
  /** "Camión KXPT-21": con HU-16 habrá una columna por camión. */
  title: string;
  subtitle?: string;
  /** Paradas en el orden que se muestra (el provisional mientras se arrastra). */
  stops: StopView[];
  /** Orden guardado en el editor (sin el arrastre en curso): de ahí sale el índice de origen. */
  order: string[];
  pinned: ReadonlySet<string>;
  /** Hay un cálculo en curso: las horas no están al día y no se puede arrastrar. */
  busy: boolean;
  selectedId: string | null;
  onSelect: (shipmentId: string) => void;
  /** Mientras se arrastra: la parada de {@code from} pasa sobre el lugar {@code to}. */
  onDrag: (from: number, to: number) => void;
  /** Al soltar. */
  onMove: (from: number, to: number) => void;
  onDragCancel: () => void;
  onTogglePin: (shipmentId: string) => void;
}

/**
 * Las paradas de un camión, para ordenarlas a mano (HU-15): se arrastran (o Alt + ↑ / ↓ con el teclado),
 * el candado fija una parada en su lugar. Es una columna para que HU-16 agregue una por camión y mover
 * paradas entre camiones sin rehacer la pantalla (docs/design/multicamion.png).
 */
export default function TruckColumn(props: Props) {
  const { stops, order, pinned, busy } = props;
  const [dragging, setDragging] = useState<string | null>(null);

  /** Índice libre más cercano en esa dirección (con el teclado, las fijadas se saltan). */
  function nextFree(from: number, dir: -1 | 1): number {
    for (let i = from + dir; i >= 0 && i < order.length; i += dir) if (!pinned.has(order[i])) return i;
    return from;
  }

  return (
    <section className="flex min-w-0 flex-1 flex-col" aria-label={props.title}>
      <header className="px-4 pb-2">
        <h2 className="text-sm font-semibold text-ink">{props.title}</h2>
        {props.subtitle && <p className="text-xs text-muted">{props.subtitle}</p>}
      </header>
      <ol className="space-y-1 px-2 pb-2" aria-label={`Paradas de ${props.title}, en orden de visita`}>
        {stops.map((s, index) => {
          const from = order.indexOf(s.shipmentId);
          const isPinned = pinned.has(s.shipmentId);
          return (
            <EditableStop
              key={s.shipmentId}
              stop={s}
              index={index}
              pinned={isPinned}
              busy={busy}
              selected={s.shipmentId === props.selectedId}
              dragging={dragging === s.shipmentId}
              onSelect={() => props.onSelect(s.shipmentId)}
              onTogglePin={() => props.onTogglePin(s.shipmentId)}
              onDragStart={() => setDragging(s.shipmentId)}
              onDragOver={() => dragging && props.onDrag(order.indexOf(dragging), index)}
              onDrop={() => {
                if (dragging) props.onMove(order.indexOf(dragging), index);
                setDragging(null);
              }}
              onDragEnd={() => {
                if (dragging) props.onDragCancel();
                setDragging(null);
              }}
              onKeyMove={(dir) => {
                const to = nextFree(from, dir);
                if (to !== from) props.onMove(from, to);
              }}
            />
          );
        })}
      </ol>
    </section>
  );
}

interface RowProps {
  stop: StopView;
  index: number;
  pinned: boolean;
  busy: boolean;
  selected: boolean;
  dragging: boolean;
  onSelect: () => void;
  onTogglePin: () => void;
  onDragStart: () => void;
  onDragOver: () => void;
  onDrop: () => void;
  onDragEnd: () => void;
  onKeyMove: (dir: -1 | 1) => void;
}

function EditableStop(p: RowProps) {
  const { stop } = p;
  const c = BAND_CLASSES[stop.band.id];
  const canDrag = !p.pinned && !p.busy;
  const display = { ...stop, position: p.index + 1 };
  return (
    <li
      draggable={canDrag}
      data-testid="editable-stop"
      data-shipment-id={stop.shipmentId}
      tabIndex={0}
      aria-label={`${p.index + 1}. ${stop.name}${p.pinned ? ", fijada" : ""}`}
      aria-roledescription="parada que se puede mover"
      onClick={p.onSelect}
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", stop.shipmentId);
        p.onDragStart();
      }}
      onDragOver={(e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
        p.onDragOver();
      }}
      onDrop={(e) => {
        e.preventDefault();
        p.onDrop();
      }}
      onDragEnd={p.onDragEnd}
      onKeyDown={(e) => {
        if (e.altKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
          e.preventDefault();
          if (canDrag) p.onKeyMove(e.key === "ArrowUp" ? -1 : 1);
        } else if (e.key === "Enter") {
          e.preventDefault();
          p.onSelect();
        }
      }}
      className={`flex gap-2 rounded-lg border px-2 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-primary ${
        p.dragging ? "border-dashed border-primary bg-tile opacity-70" : p.selected ? `bg-tile ${c.border}` : "border-transparent hover:bg-tile"
      } ${canDrag ? "cursor-grab" : ""}`}
    >
      <span className={`mt-1 shrink-0 ${canDrag ? "text-muted" : "text-line"}`} aria-hidden="true">
        <GripIcon />
      </span>
      <StopBadge stop={display} />
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <p className="truncate text-sm font-semibold text-ink">{stop.name}</p>
          <p className={`shrink-0 text-sm font-semibold tabular-nums ${p.busy ? "text-muted" : "text-ink"}`} title="Hora de llegada estimada">
            {stop.eta}
            {stop.dayOffset > 0 && <span className="ml-1 text-xs font-medium text-reject">+1 día</span>}
          </p>
        </div>
        <p className="truncate text-xs text-muted">
          {stop.address}
          {stop.commune ? `, ${stop.commune}` : ""}
          {stop.shipmentCount > 1 ? ` · ${stop.shipmentCount} envíos` : ""}
        </p>
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs">
          <span className={`rounded px-1.5 py-0.5 font-semibold ${c.bg} ${c.text}`}>Recibe {shortRange(stop.windowStart, stop.windowEnd)}</span>
          {stop.alert === "outside" && (
            <span className="flex items-center gap-1 rounded bg-reject px-1.5 py-0.5 font-semibold text-badge-ink">
              <ClockIcon className="h-3.5 w-3.5" />
              Fuera de ventana
            </span>
          )}
          {stop.alert === "near" && (
            <span className="flex items-center gap-1 rounded bg-warning-bg px-1.5 py-0.5 font-semibold text-warning-ink">
              <ClockIcon className="h-3.5 w-3.5" />
              {stop.minutesToClose} min antes del cierre
            </span>
          )}
        </div>
        <p className="mt-1 text-xs text-muted">
          {stop.serviceMinutes != null && <>descarga {stop.serviceMinutes} min · </>}
          {stop.packageCount} {stop.packageCount === 1 ? "bulto" : "bultos"}
          {stop.unloadExtraMinutes > 0 && (
            <span className="text-ink">
              {" "}
              · +{stop.unloadExtraMinutes} min{stop.unloadNote ? `, ${stop.unloadNote}` : ""}
            </span>
          )}
          {stop.waitMinutes > 0 && <span className="text-warning-ink"> · espera {stop.waitMinutes} min</span>}
        </p>
      </div>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          p.onTogglePin();
        }}
        disabled={p.busy}
        aria-pressed={p.pinned}
        aria-label={p.pinned ? `Soltar ${stop.name}` : `Fijar ${stop.name} en su posición`}
        title={p.pinned ? "Fijada: Reoptimizar el resto no la mueve" : "Fijar en esta posición"}
        className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border ${
          p.pinned ? "border-primary bg-primary text-primary-ink" : "border-border text-muted hover:bg-tile"
        } disabled:opacity-60`}
      >
        {p.pinned ? <LockIcon /> : <UnlockIcon />}
      </button>
    </li>
  );
}
