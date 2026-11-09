import { useEffect, useState } from "react";
import Layout from "@/components/Layout";
import { shipmentsApi } from "@/api/endpoints";
import type { BusinessType, Page, Shipment, ShipmentStatus } from "@/types";

const PAGE_SIZE = 20;

const STATUS_LABEL: Record<ShipmentStatus, string> = {
  PENDING: "Pendiente",
  ROUTED: "En ruta",
  OUT_FOR_DELIVERY: "En reparto",
  DELIVERED: "Entregado",
  REJECTED: "No entregado",
  RESCHEDULED: "Reprogramado",
};

const STATUS_STYLE: Record<ShipmentStatus, string> = {
  PENDING: "bg-tile text-ink",
  ROUTED: "bg-band-morning-bg text-band-morning-ink",
  OUT_FOR_DELIVERY: "bg-band-night-bg text-band-night-ink",
  DELIVERED: "bg-band-midday-bg text-band-midday-ink",
  REJECTED: "bg-tile text-reject",
  RESCHEDULED: "bg-warning-bg text-warning-ink",
};

const BUSINESS_LABEL: Record<BusinessType, string> = {
  BOTILLERIA: "Botillería",
  MINIMARKET: "Minimarket",
  BAR: "Bar",
  RESTAURANT: "Restaurant",
  OTRO: "Otro",
};

/** Fecha de hoy en la zona horaria del navegador (no en UTC), en formato YYYY-MM-DD. */
function localToday(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${d}-${m}-${y}`;
}

/** Devuelve el valor después de que deja de cambiar por `ms` milisegundos. */
function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

export default function Shipments() {
  const [date, setDate] = useState(localToday());
  const [status, setStatus] = useState<ShipmentStatus | "">("");
  const [commune, setCommune] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Shipment> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const q = useDebounced(search.trim(), 300);
  const communeFilter = useDebounced(commune.trim(), 300);

  // Cambiar un filtro vuelve a la primera página.
  useEffect(() => setPage(0), [date, status, communeFilter, q]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    shipmentsApi
      .list({
        date: date || undefined,
        status: status ? [status] : undefined,
        commune: communeFilter || undefined,
        q: q || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then((data) => !cancelled && setResult(data))
      .catch(() => !cancelled && setError("No se pudieron cargar los envíos"))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [date, status, communeFilter, q, page]);

  const total = result?.totalElements ?? 0;
  const first = total === 0 ? 0 : page * PAGE_SIZE + 1;
  const last = Math.min((page + 1) * PAGE_SIZE, total);
  const hasFilters = !!(status || commune || search);

  const inputClass =
    "rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary";

  return (
    <Layout>
      <div className="max-w-7xl mx-auto space-y-4">
        <div className="flex items-end justify-between gap-4 flex-wrap">
          <div>
            <h1 className="text-xl font-semibold text-ink">Envíos</h1>
            <p className="text-sm text-muted">Qué sale cada día, con su local y estado.</p>
          </div>
        </div>

        <form
          className="bg-panel rounded-lg border border-line p-4 flex flex-wrap items-end gap-3"
          onSubmit={(e) => e.preventDefault()}
          role="search"
        >
          <label className="flex flex-col gap-1 text-xs font-medium text-muted">
            Fecha
            <span className="flex items-center gap-2">
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className={inputClass}
                aria-label="Fecha programada"
              />
              {date ? (
                <button type="button" onClick={() => setDate("")} className="text-xs text-primary hover:underline">
                  Todas las fechas
                </button>
              ) : (
                <button type="button" onClick={() => setDate(localToday())} className="text-xs text-primary hover:underline">
                  Hoy
                </button>
              )}
            </span>
          </label>

          <label className="flex flex-col gap-1 text-xs font-medium text-muted">
            Estado
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as ShipmentStatus | "")}
              className={inputClass}
            >
              <option value="">Todos</option>
              {(Object.keys(STATUS_LABEL) as ShipmentStatus[]).map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABEL[s]}
                </option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-1 text-xs font-medium text-muted">
            Comuna
            <input
              type="text"
              value={commune}
              onChange={(e) => setCommune(e.target.value)}
              placeholder="Ej.: Ñuñoa"
              className={`${inputClass} w-40`}
            />
          </label>

          <label className="flex flex-col gap-1 text-xs font-medium text-muted flex-1 min-w-[16rem]">
            Buscar
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Local, dirección o código"
              className={inputClass}
            />
          </label>

          {hasFilters && (
            <button
              type="button"
              onClick={() => {
                setStatus("");
                setCommune("");
                setSearch("");
              }}
              className="text-sm text-muted hover:text-ink py-2"
            >
              Limpiar filtros
            </button>
          )}
        </form>

        <div className="bg-panel rounded-lg border border-line overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-tile text-left text-xs font-semibold uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-4 py-3">Fecha</th>
                  <th className="px-4 py-3">Código</th>
                  <th className="px-4 py-3">Local</th>
                  <th className="px-4 py-3">Dirección</th>
                  <th className="px-4 py-3">Comuna</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3 text-right">Bultos</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {result?.content.map((s) => (
                  <tr key={s.id} className="hover:bg-tile">
                    <td className="px-4 py-3 whitespace-nowrap text-muted">{formatDate(s.scheduledDate)}</td>
                    <td className="px-4 py-3 whitespace-nowrap font-mono text-xs text-ink">{s.trackingCode}</td>
                    <td className="px-4 py-3">
                      <div className="font-medium text-ink">{s.customer.fullName}</div>
                      {s.customer.businessType && (
                        <div className="text-xs text-muted">{BUSINESS_LABEL[s.customer.businessType]}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-muted">
                      {s.customer.address}
                      {s.customer.geocodeStatus === "REVIEW" && (
                        <span
                          className="ml-2 inline-block rounded bg-warning-bg px-1.5 py-0.5 text-xs font-medium text-warning-ink"
                          title="Ubicación aproximada o no encontrada al geocodificar"
                        >
                          Revisar
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-muted">{s.customer.commune ?? "—"}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[s.status]}`}>
                        {STATUS_LABEL[s.status]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums text-ink">{s.packageCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {error && <p className="px-4 py-6 text-sm text-reject">{error}</p>}
          {!error && !loading && total === 0 && (
            <p className="px-4 py-10 text-center text-sm text-muted">
              {date && !hasFilters
                ? `No hay envíos programados para el ${formatDate(date)}.`
                : "Ningún envío coincide con los filtros."}
            </p>
          )}

          <div className="flex items-center justify-between border-t border-line px-4 py-3 text-sm text-muted">
            <span aria-live="polite">
              {loading ? "Cargando…" : total === 0 ? "Sin resultados" : `Mostrando ${first}–${last} de ${total}`}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((p) => p - 1)}
                disabled={loading || page === 0}
                className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40 hover:bg-tile"
              >
                Anterior
              </button>
              <button
                type="button"
                onClick={() => setPage((p) => p + 1)}
                disabled={loading || !result || page + 1 >= result.totalPages}
                className="rounded-md border border-border px-3 py-1.5 disabled:opacity-40 hover:bg-tile"
              >
                Siguiente
              </button>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
}
