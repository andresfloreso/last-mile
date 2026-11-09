import { useState } from "react";
import type { TimeWindow } from "@/types";
import { declaredSpans, hourOf } from "@/lib/timeWindow";

/** Rango visible: de 07 a 22 h (HU-11). */
const FIRST_HOUR = 7;
const LAST_HOUR = 22; // la última columna es 22:00–23:00
const HOURS = Array.from({ length: LAST_HOUR - FIRST_HOUR + 1 }, (_, i) => FIRST_HOUR + i);

// Paleta categórica validada (slots 1 y 2: par adyacente, apto para daltonismo).
const ACCEPTED = "#2a78d6";
const REJECTED = "#eb6834";

const W = 640;
const H = 220;
const PAD = { top: 26, right: 8, bottom: 26, left: 28 };
const PLOT_W = W - PAD.left - PAD.right;
const PLOT_H = H - PAD.top - PAD.bottom;
const COL = PLOT_W / HOURS.length;
const BAR = Math.min(22, COL - 8);

function xOf(hour: number) {
  return PAD.left + (hour - FIRST_HOUR) * COL;
}

/** Ticks enteros "lindos" para el eje y: 0, paso, 2·paso… hasta cubrir el máximo. */
function yTicks(max: number): number[] {
  if (max <= 0) return [0];
  const step = max <= 4 ? 1 : max <= 10 ? 2 : max <= 25 ? 5 : Math.ceil(max / 5 / 5) * 5;
  const ticks = [];
  for (let v = 0; v <= max + step - 1; v += step) ticks.push(v);
  return ticks;
}

function hh(h: number) {
  return `${String(h).padStart(2, "0")}:00`;
}

/**
 * Histograma de intentos por hora de un local: recibió vs. no recibió, con la
 * modulación declarada (banda gris) y la ventana real aprendida (marco punteado).
 */
export default function TimeWindowChart({ tw }: { tw: TimeWindow }) {
  const [hover, setHover] = useState<number | null>(null);
  const buckets = tw.histogram.filter((b) => b.hour >= FIRST_HOUR && b.hour <= LAST_HOUR);
  const outside = tw.histogram
    .filter((b) => b.hour < FIRST_HOUR || b.hour > LAST_HOUR)
    .reduce((n, b) => n + b.accepted + b.rejected, 0);

  const ticks = yTicks(Math.max(...buckets.map((b) => b.accepted + b.rejected), 0));
  const yMax = ticks[ticks.length - 1] || 1;
  const yOf = (v: number) => PAD.top + PLOT_H - (v / yMax) * PLOT_H;

  const clamp = (h: number) => Math.min(Math.max(h, FIRST_HOUR), LAST_HOUR + 1);
  const spans = declaredSpans(tw.declaredWindow);
  const real = tw.realWindow;
  const realX1 = xOf(clamp(hourOf(real.start)));
  const realX2 = xOf(clamp(hourOf(real.end)));
  const hovered = hover === null ? null : tw.histogram[hover];

  return (
    <figure className="space-y-2">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600" aria-hidden="true">
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: ACCEPTED }} /> Recibió
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: REJECTED }} /> No recibió
        </span>
        {spans.length > 0 && (
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-4 rounded-sm bg-slate-200" /> Modulación (declarada)
          </span>
        )}
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-4 rounded-sm border-2 border-dashed border-slate-700" />
          {real.basis === "LEARNED" ? "Ventana real" : "Horario por defecto"}
        </span>
      </div>

      <div className="relative">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="w-full h-auto"
          role="img"
          aria-label={`Intentos de entrega por hora de ${tw.customerName}. ${tw.summary}.`}
          onMouseLeave={() => setHover(null)}
        >
          {/* Modulación: banda de fondo */}
          {spans.map(([a, b], i) => (
            <g key={i}>
              <rect x={xOf(clamp(a))} y={PAD.top} width={xOf(clamp(b)) - xOf(clamp(a))} height={PLOT_H} fill="#e2e8f0" opacity={0.7} />
              <text x={xOf(clamp(a)) + 4} y={PAD.top - 6} fontSize="10" fill="#475569">
                Modulación
              </text>
            </g>
          ))}

          {/* Grilla y eje y */}
          {ticks.map((t) => (
            <g key={t}>
              <line x1={PAD.left} x2={W - PAD.right} y1={yOf(t)} y2={yOf(t)} stroke="#e5e7eb" strokeWidth={1} />
              <text x={PAD.left - 6} y={yOf(t) + 3} fontSize="10" textAnchor="end" fill="#64748b">
                {t}
              </text>
            </g>
          ))}

          {/* Barras apiladas: recibió abajo, no recibió arriba, con 2 px de separación */}
          {buckets.map((b) => {
            const cx = xOf(b.hour) + COL / 2;
            const x = cx - BAR / 2;
            const yA = yOf(b.accepted);
            const yR = yOf(b.accepted + b.rejected);
            const gap = b.accepted > 0 && b.rejected > 0 ? 2 : 0;
            const dim = hover !== null && hover !== b.hour;
            return (
              <g key={b.hour} opacity={dim ? 0.45 : 1}>
                {b.accepted > 0 && (
                  <path d={roundedTop(x, yA, BAR, yOf(0) - yA, b.rejected === 0 ? 4 : 0)} fill={ACCEPTED} />
                )}
                {b.rejected > 0 && <path d={roundedTop(x, yR, BAR, yA - yR - gap, 4)} fill={REJECTED} />}
                {/* Área de hover más grande que la barra */}
                <rect
                  x={xOf(b.hour)}
                  y={PAD.top}
                  width={COL}
                  height={PLOT_H}
                  fill="transparent"
                  onMouseEnter={() => setHover(b.hour)}
                />
              </g>
            );
          })}

          {/* Ventana real: marco punteado encima de las barras */}
          <rect
            x={realX1 + 1}
            y={PAD.top - 1}
            width={Math.max(realX2 - realX1 - 2, 0)}
            height={PLOT_H + 1}
            fill="none"
            stroke="#334155"
            strokeWidth={2}
            strokeDasharray="5 3"
            rx={4}
            pointerEvents="none"
          />
          <text x={realX2 - 4} y={PAD.top - 6} fontSize="10" fontWeight={600} textAnchor="end" fill="#1e293b">
            {real.basis === "LEARNED" ? "Ventana real" : "Por defecto"}
          </text>

          {/* Eje x */}
          <line x1={PAD.left} x2={W - PAD.right} y1={yOf(0)} y2={yOf(0)} stroke="#94a3b8" />
          {HOURS.map((h) => (
            <text key={h} x={xOf(h) + COL / 2} y={H - 8} fontSize="10" textAnchor="middle" fill="#64748b">
              {String(h).padStart(2, "0")}
            </text>
          ))}
        </svg>

        {hovered && (
          <div
            className="pointer-events-none absolute top-0 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs shadow-md"
            style={{
              left: `${((xOf(hovered.hour) + COL / 2) / W) * 100}%`,
              transform: hovered.hour > 17 ? "translateX(-100%)" : "translateX(0)",
            }}
          >
            <p className="font-semibold text-slate-800">
              {hh(hovered.hour)}–{hh(hovered.hour + 1)}
            </p>
            <p className="text-slate-600">
              Recibió {hovered.accepted} · No recibió {hovered.rejected}
            </p>
          </div>
        )}
      </div>

      <figcaption className="text-xs text-slate-500">
        Intentos de entrega por hora (hora de Santiago), de 07 a 22 h.
        {outside > 0 && ` ${outside} ${outside === 1 ? "intento" : "intentos"} fuera de ese rango no se muestran.`}
      </figcaption>

      <details className="text-xs text-slate-600">
        <summary className="cursor-pointer select-none text-brand-700">Ver como tabla</summary>
        <table className="mt-2 w-full max-w-sm">
          <thead>
            <tr className="text-left text-slate-500">
              <th className="py-1 font-medium">Hora</th>
              <th className="py-1 font-medium text-right">Recibió</th>
              <th className="py-1 font-medium text-right">No recibió</th>
            </tr>
          </thead>
          <tbody>
            {buckets
              .filter((b) => b.accepted + b.rejected > 0)
              .map((b) => (
                <tr key={b.hour} className="border-t border-slate-100">
                  <td className="py-1">
                    {hh(b.hour)}–{hh(b.hour + 1)}
                  </td>
                  <td className="py-1 text-right tabular-nums">{b.accepted}</td>
                  <td className="py-1 text-right tabular-nums">{b.rejected}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}

/** Rectángulo con las esquinas superiores redondeadas (radio r) y la base recta sobre el eje. */
function roundedTop(x: number, y: number, w: number, h: number, r: number): string {
  if (h <= 0) return "";
  const rr = Math.min(r, w / 2, h);
  return `M${x},${y + h} V${y + rr} Q${x},${y} ${x + rr},${y} H${x + w - rr} Q${x + w},${y} ${x + w},${y + rr} V${y + h} Z`;
}
