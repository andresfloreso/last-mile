import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Layout from "@/components/Layout";
import RouteSidebar, { type LoadState } from "@/components/RouteSidebar";
import RouteEditorPanel from "@/components/RouteEditorPanel";
import RouteMap from "@/components/RouteMap";
import DayTimeline from "@/components/DayTimeline";
import type { DeclaredWindowPayload } from "@/lib/declaredWindow";
import { CloseIcon, ListIcon } from "@/components/icons";
import { customersApi, routesApi, shipmentsApi } from "@/api/endpoints";
import { buildStopViews, summarize } from "@/lib/routeView";
import { draftLine } from "@/lib/routeEdit";
import { useRouteEditor } from "@/hooks/useRouteEditor";
import { DEPARTURE_TIME, DEPOT } from "@/lib/config";
import { canBuildRoutes, canViewRoutes, unroutedCount } from "@/lib/routeAccess";
import { useAuthStore } from "@/store/authStore";
import { useRouteDateStore } from "@/store/routeDateStore";
import { useThemeStore } from "@/store/themeStore";
import type { RouteStop, Shipment, TimeWindow } from "@/types";

function errorMessage(e: unknown): string {
  const err = e as { response?: { status?: number; data?: { message?: string } } };
  if (err?.response?.status === 403) return "Tu rol no puede armar rutas: solo despacho o administración.";
  return err?.response?.data?.message ?? "No se pudo cargar la ruta del día.";
}

/**
 * Pantalla de rutas (HU-27). Es de presentación: junta lo que ya existe —
 * envíos de la fecha (HU-10), ruta con hora estimada (HU-14), ventana real por local (HU-11),
 * marca "revisar" (HU-09) y edición de la modulación (HU-25)— sin recalcular nada.
 *
 * HU-32: al abrirse lee la ruta guardada (GET /api/routes), sin efectos. Solo arma la ruta
 * (POST /api/routes/build) cuando despacho lo pide. El operador la ve en solo lectura, y
 * conductor y cliente no la ven.
 */
export default function RoutePage() {
  const role = useAuthStore((s) => s.user?.role);
  if (!canViewRoutes(role)) {
    return (
      <Layout>
        <div className="mx-auto max-w-md px-4 py-16 text-center">
          <h1 className="text-22 font-bold text-ink">Ruta del día</h1>
          <p className="mt-2 text-sm text-muted">Tu rol no ve la ruta del día: es para despacho, administración y operaciones.</p>
        </div>
      </Layout>
    );
  }
  return <RouteScreen canBuild={canBuildRoutes(role)} />;
}

function RouteScreen({ canBuild }: { canBuild: boolean }) {
  const date = useRouteDateStore((s) => s.date);
  const theme = useThemeStore((s) => s.theme);

  const [state, setState] = useState<LoadState>("loading");
  const [error, setError] = useState<string | null>(null);
  const [shipments, setShipments] = useState<Shipment[]>([]);
  const [route, setRoute] = useState<RouteStop[]>([]);
  const [timeWindows, setTimeWindows] = useState<Map<string, TimeWindow>>(new Map());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [centerKey, setCenterKey] = useState(0);
  const [relocatingId, setRelocatingId] = useState<string | null>(null);
  const [reoptimizing, setReoptimizing] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const loadId = useRef(0);

  const loadTimeWindows = useCallback(async (list: Shipment[], id: number) => {
    const customerIds = [...new Set(list.map((s) => s.customer.id))];
    const results = await Promise.allSettled(customerIds.map((c) => customersApi.timeWindow(c)));
    if (id !== loadId.current) return;
    const map = new Map<string, TimeWindow>();
    results.forEach((r) => r.status === "fulfilled" && map.set(r.value.customerId, r.value));
    setTimeWindows(map);
  }, []);

  /**
   * Carga los envíos de la fecha y su ruta. Al abrir (build = false) lee la ruta guardada, sin
   * efectos; con build = true la arma y guarda el orden (POST /api/routes/build), solo a pedido.
   */
  const load = useCallback(
    async (build = false) => {
      const id = ++loadId.current;
      if (build) setReoptimizing(true);
      else setState("loading");
      setError(null);
      try {
        const page = await shipmentsApi.list({ date, size: 500 });
        if (id !== loadId.current) return;
        if (page.totalElements === 0) {
          setShipments([]);
          setRoute([]);
          setTimeWindows(new Map());
          setState("empty");
          return;
        }
        const stops = build
          ? await routesApi.build(date, DEPOT.lat, DEPOT.lng, DEPARTURE_TIME)
          : await routesApi.saved(date, DEPOT.lat, DEPOT.lng, DEPARTURE_TIME);
        if (id !== loadId.current) return;
        setShipments(page.content);
        setRoute(stops);
        setState(stops.length === 0 ? "unbuilt" : "ready");
        loadTimeWindows(page.content, id);
      } catch (e) {
        if (id !== loadId.current) return;
        setError(errorMessage(e));
        setState("error");
      } finally {
        if (id === loadId.current) setReoptimizing(false);
      }
    },
    [date, loadTimeWindows]
  );

  // HU-15: armado manual. Al guardar, se vuelve a leer la ruta guardada.
  const editor = useRouteEditor(date, () => load(false));
  const { exit: exitEditor, current: editCurrent, baseline: editBaseline } = editor;

  useEffect(() => {
    setSelectedId(null);
    setRelocatingId(null);
    exitEditor();
    load();
  }, [load, exitEditor]);

  const savedStops = useMemo(() => buildStopViews(route, shipments, timeWindows), [route, shipments, timeWindows]);
  const editStops = useMemo(
    () => (editCurrent ? buildStopViews(editCurrent.stops, shipments, timeWindows) : []),
    [editCurrent, shipments, timeWindows]
  );
  const baselineStops = useMemo(
    () => (editBaseline ? buildStopViews(editBaseline.stops, shipments, timeWindows) : []),
    [editBaseline, shipments, timeWindows]
  );
  const editing = editor.editing;
  const stops = editing ? editStops : savedStops;
  /** Mientras se arrastra, la lista va en el orden provisional. */
  const shownEditStops = useMemo(() => {
    if (!editor.draft) return editStops;
    const byId = new Map(editStops.map((s) => [s.shipmentId, s]));
    return editor.draft.flatMap((id) => (byId.has(id) ? [byId.get(id)!] : []));
  }, [editor.draft, editStops]);
  const dragLine = useMemo(
    () => (editing && editor.draft ? draftLine(editor.draft, editStops, [DEPOT.lat, DEPOT.lng]) : null),
    [editing, editor.draft, editStops]
  );
  const ghostLine = useMemo(() => {
    if (!editing || !editor.dirty || !editor.baseline) return null;
    const line = baselineStops.flatMap((s, i) => (i === 0 ? s.path : s.path.slice(1)));
    const back = editor.baseline.summary.returnPath;
    return line.length > 1 ? [...line, ...back.slice(1)] : null;
  }, [editing, editor.dirty, editor.baseline, baselineStops]);
  const summary = useMemo(() => (state === "ready" ? summarize(stops, route) : null), [state, stops, route]);
  const unrouted = useMemo(
    () => (state === "ready" ? unroutedCount(shipments.map((s) => s.id), route.flatMap((r) => r.shipmentIds ?? [r.shipmentId])) : 0),
    [state, shipments, route]
  );
  const selected = stops.find((s) => s.shipmentId === selectedId) ?? null;

  // Escape cierra la tarjeta de detalle (o el panel en tablet).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      if (drawerOpen) setDrawerOpen(false);
      else {
        setSelectedId(null);
        setRelocatingId(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [drawerOpen]);

  function selectFromList(id: string) {
    setSelectedId(id);
    setRelocatingId(null);
    setCenterKey((k) => k + 1);
    setDrawerOpen(false);
  }

  function selectFromMap(id: string) {
    setSelectedId(id);
    if (relocatingId !== id) setRelocatingId(null);
  }

  /** Tras editar la modulación: refresca el local (envíos y /time-window), sin rearmar la ruta. */
  async function saveDeclared(payload: DeclaredWindowPayload) {
    if (!selected) return;
    const customerId = selected.customerId;
    await customersApi.updateDeclaredWindow(customerId, payload);
    const [page, tw] = await Promise.all([shipmentsApi.list({ date, size: 500 }), customersApi.timeWindow(customerId)]);
    setShipments(page.content);
    setTimeWindows((prev) => new Map(prev).set(customerId, tw));
  }

  /**
   * Corregir ubicación (HU-09): guarda el pin arrastrado. Despacho rearma la ruta con la nueva
   * posición (es una acción suya); en solo lectura se vuelve a leer la ruta guardada.
   */
  async function relocate(lat: number, lng: number) {
    if (!selected) return;
    try {
      await customersApi.updateLocation(selected.customerId, lat, lng);
      setRelocatingId(null);
      await load(canBuild);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const sidebar = editing ? (
    <RouteEditorPanel date={date} editor={editor} stops={shownEditStops} selectedId={selectedId} onSelect={selectFromList} />
  ) : (
    <RouteSidebar
      date={date}
      state={state}
      error={error}
      stops={stops}
      summary={summary}
      selectedId={selectedId}
      onSelect={selectFromList}
      onBuild={() => load(true)}
      onRetry={() => load(false)}
      reoptimizing={reoptimizing}
      canBuild={canBuild}
      unrouted={unrouted}
      onEdit={() => editor.start(route.map((r) => r.shipmentId))}
      editStarting={editor.busy === "start"}
      editError={editor.error}
    />
  );

  return (
    <Layout fullBleed>
      <div className="flex h-full">
        {/* Desktop (≥1280 px): panel fijo de 400 px. */}
        <div className="hidden w-[400px] shrink-0 border-r border-line xl:block">{sidebar}</div>

        {/* Tablet: el panel es un drawer sobre el mapa. */}
        {drawerOpen && (
          <div className="fixed inset-x-0 bottom-0 top-14 z-40 xl:hidden" role="dialog" aria-modal="true" aria-label="Paradas">
            <button
              type="button"
              aria-label="Cerrar panel de paradas"
              className="absolute inset-0 h-full w-full cursor-default bg-ink opacity-40"
              onClick={() => setDrawerOpen(false)}
            />
            <div className="absolute inset-y-0 left-0 w-[400px] max-w-[90vw] border-r border-line">
              {sidebar}
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                aria-label="Cerrar panel de paradas"
                className="absolute -right-12 top-3 rounded-lg border border-line bg-float p-2 text-ink hover:bg-tile"
              >
                <CloseIcon />
              </button>
            </div>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <div className="relative min-h-0 flex-1">
            <RouteMap
              stops={stops}
              theme={theme}
              selectedId={selectedId}
              centerKey={centerKey}
              relocatingId={relocatingId}
              timeWindow={selected ? timeWindows.get(selected.customerId) : undefined}
              onSelect={selectFromMap}
              onCloseCard={() => {
                setSelectedId(null);
                setRelocatingId(null);
              }}
              onSaveDeclared={saveDeclared}
              onStartRelocate={() => selected && setRelocatingId(selected.shipmentId)}
              onRelocated={relocate}
              returnPath={editing ? editor.current?.summary.returnPath : undefined}
              ghostLine={ghostLine}
              draftLine={dragLine}
              leftAction={
                <button
                  type="button"
                  onClick={() => setDrawerOpen(true)}
                  className="flex h-10 items-center gap-2 rounded-lg border border-line bg-float px-3 text-13 font-semibold text-ink hover:bg-tile xl:hidden"
                >
                  <ListIcon />
                  Paradas{state === "ready" ? ` (${stops.length})` : ""}
                </button>
              }
            />
            {state === "loading" && (
              <div className="absolute inset-0 z-20 flex items-center justify-center" aria-live="polite">
                <div className="absolute inset-0 bg-page opacity-60" aria-hidden="true" />
                <p className="relative rounded-lg border border-line bg-float px-4 py-2 text-13 text-muted">
                  Cargando la ruta del día…
                </p>
              </div>
            )}
          </div>
          <DayTimeline stops={state === "ready" ? stops : []} selectedId={selectedId} onSelect={selectFromList} />
        </div>
      </div>
    </Layout>
  );
}
