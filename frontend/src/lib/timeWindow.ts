import type { DeclaredWindow, TimeWindow } from "@/types";

/** "09:00" → 9, "12:30" → 12.5 */
export function hourOf(hhmm: string): number {
  const [h, m] = hhmm.split(":").map(Number);
  return h + (m || 0) / 60;
}

/** "09:00" → "9", "12:30" → "12:30" (formato corto para frases). */
export function shortTime(hhmm: string): string {
  const [h, m] = hhmm.split(":");
  return m === "00" ? String(Number(h)) : `${Number(h)}:${m}`;
}

function range(start: string, end: string): string {
  return `${shortTime(start)}–${shortTime(end)}`;
}

/** "9–12" o "9–12 y 16–18" */
export function declaredText(w: DeclaredWindow): string {
  const first = range(w.start, w.end);
  return w.start2 && w.end2 ? `${first} y ${range(w.start2, w.end2)}` : first;
}

/** Tramos de la modulación como pares [desde, hasta] en horas decimales. */
export function declaredSpans(w: DeclaredWindow | null): [number, number][] {
  if (!w) return [];
  const spans: [number, number][] = [[hourOf(w.start), hourOf(w.end)]];
  if (w.start2 && w.end2) spans.push([hourOf(w.start2), hourOf(w.end2)]);
  return spans;
}

/**
 * Frase de una línea para el despachador (HU-11), siempre con visitas y nunca con un puntaje:
 * "Declara 9–12 · recibe 12–14 (8 de 10 visitas)".
 */
export function windowLine(tw: TimeWindow): string {
  const declared = tw.declaredWindow ? `Declara ${declaredText(tw.declaredWindow)}` : "Sin modulación";
  const real = tw.realWindow;
  if (real.basis === "DEFAULT") {
    return `${declared} · sin intentos todavía (la ruta usa ${range(real.start, real.end)})`;
  }
  const visits =
    real.visits === 0
      ? "sin visitas en esa franja"
      : `${real.acceptedVisits} de ${real.visits} ${real.visits === 1 ? "visita" : "visitas"}`;
  return `${declared} · recibe ${range(real.start, real.end)} (${visits})`;
}
