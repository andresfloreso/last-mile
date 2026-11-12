import { useCallback, useRef, useState } from "react";
import { routesApi } from "@/api/endpoints";
import { DEPARTURE_TIME, DEPOT } from "@/lib/config";
import { moveStop, sameOrder } from "@/lib/routeEdit";
import type { RoutePreview } from "@/types";

/** Un paso del armado manual: el orden, las paradas fijadas y la ruta que calculó el backend. */
interface Step {
  order: string[];
  pinned: Set<string>;
  preview: RoutePreview;
}

export type EditorBusy = "start" | "preview" | "reoptimize" | "save" | null;

function messageOf(e: unknown, fallback: string): string {
  const err = e as { response?: { status?: number; data?: { message?: string } } };
  return err?.response?.data?.message ?? fallback;
}

const ids = (p: RoutePreview) => p.stops.map((s) => s.shipmentId);

/**
 * Estado del armado manual de la ruta (HU-15).
 *
 * - {@code baseline}: la ruta del sistema al empezar a editar, contra la que se comparan los indicadores
 *   ("+1,2 km vs. sistema") y que el mapa muestra en gris punteado mientras hay cambios sin guardar.
 * - {@code history}: cada cambio (mover, fijar, reoptimizar) agrega un paso; Deshacer vuelve al anterior.
 * - {@code draft}: el orden provisional mientras se arrastra; al soltar se pide la ruta al backend
 *   (POST /api/routes/preview), que recalcula las horas sin guardar nada.
 */
export function useRouteEditor(date: string, onSaved: () => void) {
  const [baseline, setBaseline] = useState<RoutePreview | null>(null);
  const [history, setHistory] = useState<Step[]>([]);
  const [draft, setDraft] = useState<string[] | null>(null);
  const [busy, setBusy] = useState<EditorBusy>(null);
  const [error, setError] = useState<string | null>(null);
  const request = useRef(0);

  const step = history[history.length - 1] ?? null;
  const editing = baseline != null && step != null;
  const dirty = editing && !sameOrder(step.order, ids(baseline));

  const preview = useCallback(
    (order: string[], pinned: Set<string>, reoptimize = false) =>
      routesApi.preview({
        date,
        depotLat: DEPOT.lat,
        depotLng: DEPOT.lng,
        departureTime: DEPARTURE_TIME,
        order,
        pinned: [...pinned],
        reoptimize,
      }),
    [date]
  );

  /** Empieza a editar desde la ruta guardada, en su orden. */
  const start = useCallback(
    async (order: string[]) => {
      const id = ++request.current;
      setBusy("start");
      setError(null);
      try {
        const base = await preview(order, new Set());
        if (id !== request.current) return;
        setBaseline(base);
        setHistory([{ order: ids(base), pinned: new Set(), preview: base }]);
      } catch (e) {
        if (id === request.current) setError(messageOf(e, "No se pudo empezar a editar la ruta."));
      } finally {
        if (id === request.current) setBusy(null);
      }
    },
    [preview]
  );

  const push = useCallback(
    async (order: string[], pinned: Set<string>, reoptimize: boolean) => {
      const id = ++request.current;
      setBusy(reoptimize ? "reoptimize" : "preview");
      setError(null);
      try {
        const next = await preview(order, pinned, reoptimize);
        if (id !== request.current) return;
        setHistory((h) => [...h, { order: ids(next), pinned, preview: next }]);
      } catch (e) {
        if (id === request.current) setError(messageOf(e, "No se pudo recalcular la ruta."));
      } finally {
        if (id === request.current) {
          setBusy(null);
          setDraft(null);
        }
      }
    },
    [preview]
  );

  /** Orden provisional mientras se arrastra la parada de {@code from} sobre {@code to}. */
  const drag = useCallback(
    (from: number, to: number) => {
      if (!step) return;
      const next = moveStop(step.order, step.pinned, from, to);
      setDraft(sameOrder(next, step.order) ? null : next);
    },
    [step]
  );

  /** Suelta la parada: pide al backend las horas del orden nuevo. */
  const move = useCallback(
    (from: number, to: number) => {
      if (!step) return;
      const next = moveStop(step.order, step.pinned, from, to);
      if (sameOrder(next, step.order)) {
        setDraft(null);
        return;
      }
      setDraft(next);
      push(next, step.pinned, false);
    },
    [step, push]
  );

  const cancelDrag = useCallback(() => setDraft(null), []);

  /** Fija o suelta una parada. No cambia el orden ni las horas: no hace falta llamar al backend. */
  const togglePin = useCallback(
    (shipmentId: string) => {
      if (!step) return;
      const pinned = new Set(step.pinned);
      if (pinned.has(shipmentId)) pinned.delete(shipmentId);
      else pinned.add(shipmentId);
      setHistory((h) => [...h, { ...step, pinned }]);
    },
    [step]
  );

  const reoptimizeRest = useCallback(() => {
    if (step) push(step.order, step.pinned, true);
  }, [step, push]);

  const undo = useCallback(() => {
    request.current++;
    setBusy(null);
    setDraft(null);
    setHistory((h) => (h.length > 1 ? h.slice(0, -1) : h));
  }, []);

  const exit = useCallback(() => {
    request.current++;
    setBaseline(null);
    setHistory([]);
    setDraft(null);
    setBusy(null);
    setError(null);
  }, []);

  /** Guarda el orden (PUT /api/routes/order) y vuelve a la vista normal. */
  const save = useCallback(async () => {
    if (!step) return;
    const id = ++request.current;
    setBusy("save");
    setError(null);
    try {
      await routesApi.saveOrder(date, step.order);
      if (id !== request.current) return;
      exit();
      onSaved();
    } catch (e) {
      if (id === request.current) {
        setError(messageOf(e, "No se pudo guardar la ruta."));
        setBusy(null);
      }
    }
  }, [date, step, exit, onSaved]);

  return {
    editing,
    dirty,
    baseline,
    current: step?.preview ?? null,
    order: step?.order ?? [],
    pinned: step?.pinned ?? new Set<string>(),
    canUndo: history.length > 1,
    draft,
    busy,
    error,
    start,
    drag,
    move,
    cancelDrag,
    togglePin,
    reoptimizeRest,
    undo,
    save,
    exit,
  };
}

export type RouteEditor = ReturnType<typeof useRouteEditor>;
