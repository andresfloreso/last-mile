import type { RouteSummary } from "@/lib/routeView";

function Kpi({ label, value, wide = false }: { label: string; value: string; wide?: boolean }) {
  return (
    <div className={`rounded-lg bg-tile px-3 py-2.5 ${wide ? "col-span-2" : ""}`}>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="truncate text-xl font-semibold tabular-nums text-ink">{value}</dd>
    </div>
  );
}

/**
 * KPIs de la ruta: 2×2 y, en ancho completo, la entrega en primera visita de 30 días.
 * Si el backend todavía no entrega un dato, se muestra "—".
 */
export default function KpiGrid({ summary }: { summary: RouteSummary | null }) {
  const dash = "—";
  const km = summary?.km != null ? `${summary.km.toLocaleString("es-CL", { maximumFractionDigits: 1 })} km` : dash;
  const end = summary?.endTime ? `${summary.endTime}${summary.endDayOffset > 0 ? " +1" : ""}` : dash;
  const onTime = summary && summary.stops > 0 ? `${summary.onTime} de ${summary.stops}` : dash;
  return (
    <dl className="grid grid-cols-2 gap-2">
      <Kpi label="Locales" value={summary ? String(summary.stops) : dash} />
      <Kpi label="Recorrido" value={km} />
      <Kpi label="Hora de término" value={end} />
      <Kpi label="Llegan dentro de su ventana" value={onTime} />
      {/* El indicador principal del producto; el backend aún no lo calcula (sin endpoint). */}
      <Kpi label="Entrega en 1ª visita · 30 días" value={dash} wide />
    </dl>
  );
}
