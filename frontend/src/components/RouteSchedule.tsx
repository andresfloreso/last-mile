import type { RouteStop } from "@/types";
import { OUTSIDE_WINDOW_COLOR } from "@/components/RouteMap";

function hhmm(t: string) {
  return t.substring(0, 5);
}

function km(meters: number) {
  return (meters / 1000).toLocaleString("es-CL", { maximumFractionDigits: 1 });
}

/** Horario estimado de la ruta optimizada (HU-14): hora por parada y paradas fuera de su ventana real. */
export default function RouteSchedule({ route, departureTime }: { route: RouteStop[]; departureTime: string }) {
  const last = route[route.length - 1];
  const totalMeters = route.reduce((sum, r) => sum + (r.legDistanceMeters ?? 0), 0);
  const outside = route.filter((r) => r.outsideWindow).length;
  const nextDay = route.filter((r) => r.dayOffset > 0).length;
  const estimated = route.some((r) => r.timingSource === "ESTIMATED");
  const unlocated = route.filter((r) => r.timingSource === "NO_LOCATION").length;

  return (
    <section className="bg-white rounded-2xl border border-slate-200 overflow-hidden" aria-labelledby="ruta-del-dia">
      <div className="px-5 py-4 border-b border-slate-200">
        <h2 id="ruta-del-dia" className="text-base font-semibold text-slate-800">
          Ruta del día: hora estimada por parada
        </h2>
        <dl className="mt-3 grid grid-cols-2 sm:grid-cols-5 gap-3 text-sm">
          <Stat label="Paradas" value={String(route.length)} />
          <Stat label="Recorrido" value={`${km(totalMeters)} km`} />
          <Stat label="Salida" value={departureTime} />
          <Stat
            label="Término estimado"
            value={`${hhmm(last.estimatedDeparture)}${last.dayOffset > 0 ? " (+1 día)" : ""}`}
          />
          <Stat label="Fuera de ventana" value={String(outside)} highlight={outside > 0} />
        </dl>

        <div className="mt-3 space-y-1 text-xs">
          {estimated && (
            <p className="text-amber-700">
              OSRM no respondió: los tiempos se estimaron en línea recta a velocidad urbana y el mapa une las paradas
              en línea recta.
            </p>
          )}
          {nextDay > 0 && (
            <p className="text-red-700">
              La ruta no alcanza en el día: {nextDay} {nextDay === 1 ? "parada llegaría" : "paradas llegarían"} al día
              siguiente. Conviene repartirla en más camiones (HU-16) o salir antes.
            </p>
          )}
          {unlocated > 0 && (
            <p className="text-slate-600">
              {unlocated} {unlocated === 1 ? "local no tiene" : "locales no tienen"} coordenadas: su hora no incluye el
              viaje.
            </p>
          )}
        </div>
      </div>

      <div className="overflow-x-auto max-h-[28rem]">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 sticky top-0">
            <tr>
              <th className="px-4 py-2">#</th>
              <th className="px-4 py-2">Hora estimada</th>
              <th className="px-4 py-2">Local</th>
              <th className="px-4 py-2">Ventana real</th>
              <th className="px-4 py-2 text-right">Viaje</th>
              <th className="px-4 py-2">Estado</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {route.map((r) => (
              <tr key={r.shipmentId} className={r.outsideWindow ? "bg-red-50/60" : undefined}>
                <td className="px-4 py-2 tabular-nums text-slate-500">{r.position}</td>
                <td className="px-4 py-2 tabular-nums whitespace-nowrap">
                  {/* La hora estimada es el inicio de la entrega: llegada + espera hasta que abre la ventana. */}
                  <span className="font-medium text-slate-800">{hhmm(r.serviceStart)}</span>
                  {r.dayOffset > 0 && <span className="ml-1 text-xs text-red-700">+1 día</span>}
                  {r.waitMinutes > 0 && (
                    <div className="text-xs text-slate-500">
                      llega {hhmm(r.estimatedArrival)} · espera {r.waitMinutes} min
                    </div>
                  )}
                </td>
                <td className="px-4 py-2">
                  <div className="text-slate-800">{r.customerName}</div>
                  {r.orderPreference !== "AUTO" && (
                    <div className="text-xs text-slate-500">
                      {r.orderPreference === "FIRST" ? "Fijada primera" : "Fijada última"}
                    </div>
                  )}
                </td>
                <td className="px-4 py-2 tabular-nums text-slate-600 whitespace-nowrap">
                  {hhmm(r.windowStart)}–{hhmm(r.windowEnd)}
                </td>
                <td className="px-4 py-2 tabular-nums text-right text-slate-600">
                  {r.travelMinutes == null ? "—" : `${r.travelMinutes} min`}
                </td>
                <td className="px-4 py-2 whitespace-nowrap">
                  {r.outsideWindow ? (
                    <span className="font-medium" style={{ color: OUTSIDE_WINDOW_COLOR }}>
                      ⏰ Fuera de ventana
                    </span>
                  ) : (
                    <span className="text-emerald-700">Dentro de ventana</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Stat({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className="rounded-md bg-slate-50 p-2.5">
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={`text-base font-semibold tabular-nums ${highlight ? "text-red-700" : "text-slate-800"}`}>{value}</dd>
    </div>
  );
}
