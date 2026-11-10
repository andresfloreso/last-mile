import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import type { RouteSummary, StopView } from "@/lib/routeView";
import { listOrder, matchesSearch } from "@/lib/routeView";
import { longDate } from "@/lib/timeWindows";
import { DEPARTURE_TIME } from "@/lib/config";
import KpiGrid from "@/components/KpiGrid";
import StopList from "@/components/StopList";
import { SearchIcon } from "@/components/icons";

/** unbuilt: hay envíos, pero la ruta de la fecha todavía no se arma (HU-32). */
export type LoadState = "loading" | "ready" | "empty" | "unbuilt" | "error";

interface Props {
  date: string;
  state: LoadState;
  error: string | null;
  stops: StopView[];
  summary: RouteSummary | null;
  selectedId: string | null;
  onSelect: (shipmentId: string) => void;
  /** Arma o rearma la ruta (POST /api/routes/build). */
  onBuild: () => void;
  /** Vuelve a leer la ruta guardada, tras un error. */
  onRetry: () => void;
  reoptimizing: boolean;
  /** HU-32: solo despacho y administración arman la ruta; el operador la ve en solo lectura. */
  canBuild: boolean;
  /** Envíos de la fecha que no están en la ruta guardada. */
  unrouted: number;
}

function Skeleton() {
  return (
    <div className="space-y-2 px-4 py-3" aria-hidden="true">
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i} className="flex gap-3 py-2">
          <div className="h-7 w-7 animate-pulse rounded-full bg-tile" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 w-2/3 animate-pulse rounded bg-tile" />
            <div className="h-3 w-1/2 animate-pulse rounded bg-tile" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Panel izquierdo de la pantalla de rutas (HU-27): camión, KPIs, buscador, paradas y acciones. */
export default function RouteSidebar(props: Props) {
  const { date, state, error, stops, summary, selectedId, onSelect, onBuild, onRetry, reoptimizing, canBuild, unrouted } = props;
  const [query, setQuery] = useState("");
  const visible = useMemo(() => listOrder(stops).filter((s) => matchesSearch(s, query)), [stops, query]);

  return (
    <aside className="flex h-full w-full flex-col bg-panel" aria-label="Ruta del día">
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="space-y-3 px-4 pb-3 pt-4">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted">Camión 1 · Salida {DEPARTURE_TIME}</p>
              <h1 className="text-22 font-bold text-ink">{longDate(date)}</h1>
            </div>
            {/* Estados de ruta llegan con HU-17; por ahora toda ruta es un borrador. */}
            <span className="rounded-md bg-warning-bg px-2 py-0.5 text-xs font-semibold text-warning-ink">Borrador</span>
          </div>

          {state === "loading" ? (
            <div className="grid grid-cols-2 gap-2" aria-hidden="true">
              {Array.from({ length: 5 }, (_, i) => (
                <div key={i} className={`h-16 animate-pulse rounded-lg bg-tile ${i === 4 ? "col-span-2" : ""}`} />
              ))}
            </div>
          ) : (
            <KpiGrid summary={state === "ready" ? summary : null} />
          )}

          <label className="relative block">
            <span className="sr-only">Buscar local, dirección o código</span>
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar local, dirección o código"
              disabled={state !== "ready"}
              className="h-10 w-full rounded-lg border border-border bg-panel pl-9 pr-3 text-13 text-ink placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-60"
            />
          </label>
        </div>

        <div className="border-t border-line">
          {state === "loading" && <Skeleton />}
          {state === "error" && (
            <div className="space-y-3 px-4 py-8 text-center" role="alert">
              <p className="text-sm text-ink">{error ?? "No se pudo cargar la ruta del día."}</p>
              <button
                type="button"
                onClick={onRetry}
                className="h-10 rounded-lg border border-border px-4 text-13 font-semibold text-ink hover:bg-tile"
              >
                Reintentar
              </button>
            </div>
          )}
          {state === "empty" && (
            <div className="space-y-3 px-4 py-8 text-center">
              <p className="text-sm font-semibold text-ink">No hay envíos para esta fecha</p>
              <p className="text-13 text-muted">Importa la planilla del día o elige otra fecha arriba.</p>
              <Link
                to="/import"
                className="inline-flex h-10 items-center rounded-lg bg-primary px-4 text-13 font-semibold text-primary-ink"
              >
                Importar planilla
              </Link>
            </div>
          )}
          {state === "unbuilt" && (
            <div className="space-y-3 px-4 py-8 text-center">
              <p className="text-sm font-semibold text-ink">La ruta de este día todavía no está armada</p>
              <p className="text-13 text-muted">
                {canBuild
                  ? "Ármala para ordenar los envíos y ver la hora estimada de cada parada."
                  : "Despacho la arma; cuando esté lista, la verás aquí."}
              </p>
              {canBuild && (
                <button
                  type="button"
                  onClick={onBuild}
                  disabled={reoptimizing}
                  className="inline-flex h-10 items-center rounded-lg bg-primary px-4 text-13 font-semibold text-primary-ink disabled:opacity-60"
                >
                  {reoptimizing ? "Armando…" : "Armar ruta"}
                </button>
              )}
            </div>
          )}
          {state === "ready" && unrouted > 0 && (
            <p className="mx-4 mt-3 rounded-lg bg-warning-bg px-3 py-2 text-13 text-warning-ink" role="status">
              {unrouted === 1 ? "1 envío de esta fecha no está en la ruta." : `${unrouted} envíos de esta fecha no están en la ruta.`}{" "}
              {canBuild ? "Reoptimiza para incluirlos." : "Despacho debe reoptimizar para incluirlos."}
            </p>
          )}
          {state === "ready" && <StopList stops={visible} selectedId={selectedId} onSelect={onSelect} />}
        </div>
      </div>

      <div className="flex shrink-0 gap-2 border-t border-line bg-panel p-3">
        {canBuild ? (
          <button
            type="button"
            onClick={onBuild}
            disabled={reoptimizing || state !== "ready"}
            className="h-11 flex-1 rounded-lg border border-border text-sm font-semibold text-ink hover:bg-tile disabled:opacity-60"
          >
            {reoptimizing ? "Reoptimizando…" : "Reoptimizar"}
          </button>
        ) : (
          <p className="flex h-11 flex-1 items-center justify-center rounded-lg bg-tile text-13 text-muted">Solo lectura</p>
        )}
        {/* Un botón deshabilitado no muestra su title en todos los navegadores: el tooltip va en el contenedor. */}
        <span className="flex-1" title="Disponible en HU-17">
          <button
            type="button"
            disabled
            aria-describedby="confirmar-hu17"
            className="h-11 w-full cursor-not-allowed rounded-lg bg-primary text-sm font-semibold text-primary-ink opacity-60"
          >
            Confirmar y asignar
          </button>
          <span id="confirmar-hu17" className="sr-only">
            Disponible en HU-17
          </span>
        </span>
      </div>
    </aside>
  );
}
