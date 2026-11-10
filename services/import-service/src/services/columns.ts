/**
 * Columnas de la planilla de importación (HU-25) y conversión de los campos
 * de local: tipo de local y modulación (horario de recepción declarado).
 * Los parsers de Excel y CSV y la plantilla descargable usan esta misma
 * definición, así no pueden desalinearse.
 */

export interface ImportColumn {
  key: string;
  required: boolean;
  example: string;
  description: string;
}

export const IMPORT_COLUMNS: ImportColumn[] = [
  { key: "cliente", required: true, example: "Botillería El Roble", description: "Nombre del local" },
  { key: "tipo_local", required: false, example: "botillería", description: "botillería, minimarket, bar, restaurant u otro" },
  { key: "telefono", required: false, example: "+56222345678", description: "Teléfono del local" },
  { key: "direccion", required: true, example: "Av. Providencia 2124", description: "Calle y número" },
  { key: "comuna", required: false, example: "Providencia", description: "Comuna" },
  { key: "latitud", required: false, example: "-33.4210", description: "Si falta, se geocodifica la dirección" },
  { key: "longitud", required: false, example: "-70.6080", description: "Si falta, se geocodifica la dirección" },
  { key: "codigo_externo", required: false, example: "LOC-0001", description: "Código del local en tu sistema; evita duplicarlo al reimportar" },
  { key: "modulacion_desde", required: false, example: "09:00", description: "Inicio del horario de recepción que declara el local (HH:mm)" },
  { key: "modulacion_hasta", required: false, example: "12:00", description: "Término del horario de recepción que declara el local (HH:mm)" },
  { key: "tracking", required: true, example: "PED-0001", description: "Código único del envío" },
  { key: "fecha_entrega", required: true, example: "2026-12-01", description: "yyyy-mm-dd o dd-mm-yyyy" },
  { key: "peso_kg", required: false, example: "24,5", description: "Peso total del envío en kg. Si falta: 10 kg" },
  { key: "volumen_m3", required: false, example: "0,08", description: "Volumen total del envío en m³. Si falta: 0,05 m³" },
  { key: "notas", required: false, example: "Entregar por la puerta lateral", description: "Notas para el conductor" },
];

export const REQUIRED_HEADERS = IMPORT_COLUMNS.filter((c) => c.required).map((c) => c.key);

/** Normaliza encabezados: minúsculas, sin tildes y con "_" en vez de espacios. */
export function normalizeHeader(raw: string): string {
  return raw
    .toString()
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/\s+/g, "_");
}

const BUSINESS_TYPES: Record<string, string> = {
  botilleria: "BOTILLERIA",
  minimarket: "MINIMARKET",
  "mini market": "MINIMARKET",
  bar: "BAR",
  restaurant: "RESTAURANT",
  restaurante: "RESTAURANT",
  restoran: "RESTAURANT",
  otro: "OTRO",
};

export type FieldResult<T> = { ok: true; value?: T } | { ok: false; error: string };

/** "Botillería" -> BOTILLERIA. Vacío -> sin valor. */
export function parseBusinessType(raw: string | undefined): FieldResult<string> {
  if (!raw || raw.trim() === "") return { ok: true };
  const key = raw.trim().toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  const value = BUSINESS_TYPES[key];
  return value
    ? { ok: true, value }
    : { ok: false, error: `tipo_local inválido: "${raw.trim()}" (usa botillería, minimarket, bar, restaurant u otro)` };
}

function pad(n: number): string {
  return n.toString().padStart(2, "0");
}

/**
 * Hora del día como "HH:mm". Acepta "9", "09:00", "9:30", "09.30", "9 h",
 * fracciones de día (0.375 = 09:00, como guarda Excel las horas) y Date
 * (ExcelJS entrega las celdas con formato de hora como 1899-12-30THH:mm UTC).
 * Devuelve undefined si está vacío y null si no es una hora válida.
 */
export function parseTimeOfDay(raw: unknown): string | undefined | null {
  if (raw === null || raw === undefined) return undefined;

  if (raw instanceof Date) {
    if (Number.isNaN(raw.getTime())) return null;
    return `${pad(raw.getUTCHours())}:${pad(raw.getUTCMinutes())}`;
  }

  if (typeof raw === "number") {
    if (raw >= 0 && raw < 1) {
      const minutes = Math.round(raw * 24 * 60);
      return `${pad(Math.floor(minutes / 60) % 24)}:${pad(minutes % 60)}`;
    }
    return Number.isInteger(raw) && raw >= 0 && raw <= 23 ? `${pad(raw)}:00` : null;
  }

  const text = String(raw).trim();
  if (text === "") return undefined;
  const match = text.match(/^(\d{1,2})(?:[:.](\d{2}))?(?::\d{2})?\s*(?:h|hrs|horas)?$/i);
  if (!match) return null;
  const hours = Number(match[1]);
  const minutes = match[2] ? Number(match[2]) : 0;
  if (hours > 23 || minutes > 59) return null;
  return `${pad(hours)}:${pad(minutes)}`;
}

export interface Modulation {
  declaredWindowStart?: string;
  declaredWindowEnd?: string;
}

/** Modulación de una fila: ambos extremos o ninguno, y el inicio antes del término. */
export function parseModulation(fromRaw: unknown, toRaw: unknown): FieldResult<Modulation> {
  const start = parseTimeOfDay(fromRaw);
  const end = parseTimeOfDay(toRaw);

  if (start === null) return { ok: false, error: `modulacion_desde no es una hora válida: "${String(fromRaw)}" (usa HH:mm)` };
  if (end === null) return { ok: false, error: `modulacion_hasta no es una hora válida: "${String(toRaw)}" (usa HH:mm)` };
  if (start === undefined && end === undefined) return { ok: true, value: {} };
  if (start === undefined || end === undefined) {
    return { ok: false, error: "La modulación necesita modulacion_desde y modulacion_hasta" };
  }
  if (start >= end) {
    return { ok: false, error: `Modulación inválida: modulacion_desde (${start}) debe ser anterior a modulacion_hasta (${end})` };
  }
  return { ok: true, value: { declaredWindowStart: start, declaredWindowEnd: end } };
}

export interface Load {
  weightKg?: number;
  volumeM3?: number;
}

/** Número positivo con punto o coma decimal ("24,5"). Vacío: undefined; inválido: null. */
export function parsePositiveNumber(raw: unknown): number | undefined | null {
  if (raw === null || raw === undefined) return undefined;
  if (typeof raw === "number") return Number.isFinite(raw) && raw > 0 ? raw : null;
  const text = String(raw).trim();
  if (text === "") return undefined;
  if (!/^\d+([.,]\d+)?$/.test(text)) return null;
  const value = Number(text.replace(",", "."));
  return value > 0 ? value : null;
}

/**
 * Peso y volumen del envío (HU-29). Son opcionales: si faltan, delivery-service usa el valor
 * por defecto documentado (10 kg y 0,05 m³ por envío).
 */
export function parseLoad(weightRaw: unknown, volumeRaw: unknown): FieldResult<Load> {
  const weightKg = parsePositiveNumber(weightRaw);
  const volumeM3 = parsePositiveNumber(volumeRaw);
  if (weightKg === null) return { ok: false, error: `peso_kg debe ser un número mayor que 0: "${String(weightRaw)}"` };
  if (volumeM3 === null) return { ok: false, error: `volumen_m3 debe ser un número mayor que 0: "${String(volumeRaw)}"` };
  return { ok: true, value: { weightKg, volumeM3 } };
}

/** Primer error de una lista de resultados de campos, o undefined si todos están bien. */
export function firstError(...results: FieldResult<unknown>[]): string | undefined {
  for (const r of results) if (!r.ok) return r.error;
  return undefined;
}
