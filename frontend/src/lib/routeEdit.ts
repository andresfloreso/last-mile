import { bandForHour, hourOf, type BandId } from "@/lib/timeWindows";

/**
 * Armado manual de la ruta (HU-15) y colores de la línea por hora de manejo. Funciones puras: la
 * pantalla las usa para redibujar mientras se arrastra, sin esperar al backend.
 */

type LatLng = [number, number];

/** Lo mínimo de una parada para dibujar la línea. */
export interface LegStop {
  shipmentId: string;
  position: number;
  latitude: number | null;
  longitude: number | null;
  /** Tramo desde la parada anterior (o la bodega). */
  path: LatLng[];
  /** Salida del local, HH:mm. */
  departure: string;
  dayOffset: number;
}

export interface Segment {
  band: BandId;
  positions: LatLng[];
}

/** Franja de la hora a la que el camión maneja un tramo: 07–11:59, 12–14:59, 15–18:59, 19 en adelante. */
export function driveBand(time: string, dayOffset = 0): BandId {
  return bandForHour(hourOf(time) + 24 * dayOffset).id;
}

/**
 * La línea de la ruta por tramos, cada uno del color de la hora a la que el camión lo maneja (HU-15).
 * Cada tramo toma la hora de salida de la parada anterior (el primero, la salida de la bodega).
 * Los tramos seguidos de la misma franja se juntan en una sola línea.
 *
 * @param back vuelta a la bodega desde la última parada, si se conoce
 */
export function routeSegments(stops: LegStop[], departure: string, back: LatLng[] = []): Segment[] {
  const ordered = [...stops].sort((a, b) => a.position - b.position);
  const segments: Segment[] = [];
  let band = driveBand(departure);
  const push = (path: LatLng[]) => {
    if (path.length < 2) return;
    const last = segments[segments.length - 1];
    if (last && last.band === band) last.positions.push(...path.slice(1));
    else segments.push({ band, positions: [...path] });
  };
  for (const s of ordered) {
    push(s.path);
    band = driveBand(s.departure, s.dayOffset);
  }
  push(back);
  return segments;
}

/**
 * Mueve la parada de la posición {@code from} a {@code to} (índices de la lista). Las fijadas no se
 * mueven ni se corren: la parada se mueve entre los lugares libres.
 */
export function moveStop(order: string[], pinned: ReadonlySet<string>, from: number, to: number): string[] {
  const id = order[from];
  if (id == null || pinned.has(id) || from === to) return order;
  const freeSlots = order.map((s, i) => (pinned.has(s) ? -1 : i)).filter((i) => i >= 0);
  const free = freeSlots.map((i) => order[i]);
  const fromFree = free.indexOf(id);
  // Al soltar sobre una fijada, la parada queda en el lugar libre más cercano antes de ella (si baja)
  // o después de ella (si sube).
  const target = Math.min(order.length - 1, Math.max(0, to));
  let toFree =
    target > from
      ? freeSlots.reduce((best, slot, k) => (slot <= target ? k : best), fromFree)
      : freeSlots.findIndex((slot) => slot >= target);
  if (toFree < 0) toFree = fromFree;
  free.splice(fromFree, 1);
  free.splice(toFree, 0, id);
  const next = [...order];
  freeSlots.forEach((slot, k) => (next[slot] = free[k]));
  return next;
}

/**
 * Línea provisional mientras se arrastra: el orden nuevo, con el tramo por calle si ya se conoce
 * (los dos extremos ya iban seguidos) o una recta si no. Al soltar, el backend la reemplaza.
 */
export function draftLine(order: string[], stops: LegStop[], depot: LatLng): LatLng[] {
  const byId = new Map(stops.map((s) => [s.shipmentId, s]));
  const known = new Map<string, LatLng[]>();
  const sorted = [...stops].sort((a, b) => a.position - b.position);
  let prev = "bodega";
  for (const s of sorted) {
    if (s.latitude == null || s.longitude == null) continue;
    known.set(`${prev}>${s.shipmentId}`, s.path);
    prev = s.shipmentId;
  }
  const line: LatLng[] = [depot];
  let from = "bodega";
  let at: LatLng = depot;
  for (const id of order) {
    const s = byId.get(id);
    if (!s || s.latitude == null || s.longitude == null) continue;
    const to: LatLng = [s.latitude, s.longitude];
    const path = known.get(`${from}>${id}`);
    line.push(...(path && path.length > 1 ? path.slice(1) : [to]));
    from = id;
    at = to;
  }
  if (at !== depot) line.push(depot);
  return line;
}

/** Aviso de la parada frente a su ventana real (HU-15): fuera (rojo) o a menos de 60 min del cierre (ámbar). */
export type WindowAlert = "outside" | "near" | null;

export const NEAR_EDGE_MINUTES = 60;

export function windowAlert(serviceStart: string, windowEnd: string, outsideWindow: boolean, dayOffset = 0): {
  alert: WindowAlert;
  minutesToClose: number | null;
} {
  if (outsideWindow || dayOffset > 0) return { alert: "outside", minutesToClose: null };
  const left = Math.round((hourOf(windowEnd) - hourOf(serviceStart)) * 60);
  return { alert: left < NEAR_EDGE_MINUTES ? "near" : null, minutesToClose: left };
}

/** "+1,2 km vs. sistema", "−0,4 km vs. sistema" o "igual que el sistema". */
export function kmDelta(meters: number, baseMeters: number): string {
  const diff = Math.round((meters - baseMeters) / 100) / 10;
  if (diff === 0) return "igual que el sistema";
  const text = Math.abs(diff).toLocaleString("es-CL", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  return `${diff > 0 ? "+" : "−"}${text} km vs. sistema`;
}

/** "+14 min vs. sistema", "−1 h 05 min vs. sistema" o "igual que el sistema". */
export function timeDelta(time: string | null, dayOffset: number, base: string | null, baseOffset: number): string {
  if (!time || !base) return "—";
  const diff = Math.round((hourOf(time) + 24 * dayOffset - hourOf(base) - 24 * baseOffset) * 60);
  if (diff === 0) return "igual que el sistema";
  const abs = Math.abs(diff);
  const text = abs >= 60 ? `${Math.floor(abs / 60)} h ${String(abs % 60).padStart(2, "0")} min` : `${abs} min`;
  return `${diff > 0 ? "+" : "−"}${text} vs. sistema`;
}

/** "+1 vs. sistema", "−2 vs. sistema" o "igual que el sistema". */
export function countDelta(value: number, base: number): string {
  const diff = value - base;
  if (diff === 0) return "igual que el sistema";
  return `${diff > 0 ? "+" : "−"}${Math.abs(diff)} vs. sistema`;
}

/** ¿El orden cambió? */
export function sameOrder(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((id, i) => id === b[i]);
}
