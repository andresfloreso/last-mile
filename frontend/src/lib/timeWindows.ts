import type { DeclaredWindow } from "@/types";

/**
 * Franjas del día y colores de la pantalla de rutas (HU-27).
 *
 * La franja de una parada sale del INICIO de su ventana real:
 * Mañana 07–12, Mediodía 12–15, Tarde 15–19, Noche 19–22.
 * Los colores son tokens CSS (src/index.css), así funcionan en claro y oscuro.
 */
export type BandId = "morning" | "midday" | "afternoon" | "night";

export interface Band {
  id: BandId;
  label: string;
  /** Hora de inicio, inclusive. */
  start: number;
  /** Hora de término, exclusiva. */
  end: number;
}

export const BANDS: readonly Band[] = [
  { id: "morning", label: "Mañana", start: 7, end: 12 },
  { id: "midday", label: "Mediodía", start: 12, end: 15 },
  { id: "afternoon", label: "Tarde", start: 15, end: 19 },
  { id: "night", label: "Noche", start: 19, end: 22 },
];

/** Inicio y fin del eje de la línea de tiempo y del histograma. */
export const DAY_START = 7;
export const DAY_END = 22;

/** "09:30" o "09:30:00" → 9.5 */
export function hourOf(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h + (m || 0) / 60;
}

/**
 * Franja que corresponde a una hora. Antes de las 07 cuenta como Mañana y desde las 22
 * como Noche: toda parada tiene franja.
 */
export function bandForHour(hour: number): Band {
  if (hour < 12) return BANDS[0];
  if (hour < 15) return BANDS[1];
  if (hour < 19) return BANDS[2];
  return BANDS[3];
}

/** Franja de una parada, según el inicio de su ventana real ("12:00:00" → Mediodía). */
export function bandForWindowStart(windowStart: string): Band {
  return bandForHour(hourOf(windowStart));
}

/** Variables CSS de una franja, para estilos en línea (marcadores de Leaflet, SVG). */
export function bandVars(id: BandId) {
  return {
    solid: `var(--band-${id})`,
    bg: `var(--band-${id}-bg)`,
    text: `var(--band-${id}-text)`,
  };
}

/** Clases de Tailwind de una franja (la lista literal es necesaria para que Tailwind las genere). */
export const BAND_CLASSES: Record<BandId, { solid: string; bg: string; text: string; border: string }> = {
  morning: { solid: "bg-band-morning", bg: "bg-band-morning-bg", text: "text-band-morning-ink", border: "border-band-morning" },
  midday: { solid: "bg-band-midday", bg: "bg-band-midday-bg", text: "text-band-midday-ink", border: "border-band-midday" },
  afternoon: { solid: "bg-band-afternoon", bg: "bg-band-afternoon-bg", text: "text-band-afternoon-ink", border: "border-band-afternoon" },
  night: { solid: "bg-band-night", bg: "bg-band-night-bg", text: "text-band-night-ink", border: "border-band-night" },
};

/** "09:00" → "09", "09:30" → "09:30" */
function short(time: string): string {
  const [h, m] = time.split(":");
  return m && m !== "00" ? `${h.padStart(2, "0")}:${m}` : h.padStart(2, "0");
}

/** Rango corto para chips: ("12:00", "15:00") → "12–15"; ("09:30", "12:00") → "09:30–12". */
export function shortRange(start: string, end: string): string {
  return `${short(start)}–${short(end)}`;
}

/** Rango largo para la tarjeta de detalle: ("09:00:00", "12:00") → "09:00–12:00". */
export function longRange(start: string, end: string): string {
  return `${start.substring(0, 5)}–${end.substring(0, 5)}`;
}

/** Tramos de la modulación como pares [desde, hasta] en horas decimales. */
export function declaredSpans(w: DeclaredWindow | null | undefined): [number, number][] {
  if (!w) return [];
  const spans: [number, number][] = [[hourOf(w.start), hourOf(w.end)]];
  if (w.start2 && w.end2) spans.push([hourOf(w.start2), hourOf(w.end2)]);
  return spans;
}

/** "Declara 09–12" o "Declara 09–12 y 17–19" (sin el prefijo). */
export function declaredText(w: DeclaredWindow): string {
  const first = shortRange(w.start, w.end);
  return w.start2 && w.end2 ? `${first} y ${shortRange(w.start2, w.end2)}` : first;
}

/**
 * ¿La modulación no coincide con la ventana real? Coincide si el inicio de la ventana
 * real cae dentro de algún tramo declarado (el mismo criterio que usa SeedDataIT).
 * Sin modulación no hay nada que comparar: no difiere.
 */
export function declaredDiffers(declared: DeclaredWindow | null | undefined, realStart: string): boolean {
  const spans = declaredSpans(declared);
  if (spans.length === 0) return false;
  const h = hourOf(realStart);
  return !spans.some(([a, b]) => h >= a && h < b);
}

/** Posición (0 a 1) de una hora en el eje 07–22; fuera del eje se recorta a los bordes. */
export function axisPosition(hour: number, start = DAY_START, end = DAY_END): number {
  return Math.min(1, Math.max(0, (hour - start) / (end - start)));
}

/** "2026-11-09" → "Lunes 9 de noviembre" */
export function longDate(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const text = new Intl.DateTimeFormat("es-CL", { weekday: "long", day: "numeric", month: "long" }).format(
    new Date(y, m - 1, d)
  );
  return text.charAt(0).toUpperCase() + text.slice(1).replace(",", "");
}

/** Fecha de hoy en la zona horaria del navegador (no en UTC), YYYY-MM-DD. */
export function localToday(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
