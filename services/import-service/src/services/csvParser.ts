import Papa from "papaparse";
import { ParseResult, ParsedShipmentRow, RowError } from "../types/shipment";

const REQUIRED_HEADERS = ["cliente", "direccion", "tracking", "fecha_entrega"];

function normalizeHeader(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\s+/g, "_");
}

function toNumber(value?: string): number | undefined {
  if (!value) return undefined;
  const num = Number(value.replace(",", "."));
  return Number.isFinite(num) ? num : undefined;
}

function toIsoDate(value?: string): string | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
  const dmy = trimmed.match(/^(\d{2})[/-](\d{2})[/-](\d{4})$/);
  if (dmy) {
    const [, d, m, y] = dmy;
    return `${y}-${m}-${d}`;
  }
  return undefined;
}

export function parseShipmentsCsv(fileContent: string): ParseResult {
  const parsed = Papa.parse<Record<string, string>>(fileContent, {
    header: true,
    skipEmptyLines: true,
    transformHeader: normalizeHeader,
  });

  const errors: RowError[] = [];
  const rows: ParsedShipmentRow[] = [];

  const headers = parsed.meta.fields || [];
  const missing = REQUIRED_HEADERS.filter((h) => !headers.includes(h));
  if (missing.length > 0) {
    return { rows: [], errors: [{ rowNumber: 1, message: `Faltan columnas obligatorias: ${missing.join(", ")}` }] };
  }

  parsed.data.forEach((record, idx) => {
    const rowNumber = idx + 2; // +1 por header, +1 por índice base 1
    const customerName = record["cliente"]?.trim();
    const address = record["direccion"]?.trim();
    const trackingCode = record["tracking"]?.trim();
    const scheduledDate = toIsoDate(record["fecha_entrega"]);

    if (!customerName && !address && !trackingCode) return;

    if (!customerName || !address || !trackingCode || !scheduledDate) {
      errors.push({ rowNumber, message: "Faltan datos obligatorios o fecha_entrega inválida" });
      return;
    }

    rows.push({
      rowNumber,
      customerName,
      phone: record["telefono"]?.trim() || undefined,
      address,
      commune: record["comuna"]?.trim() || undefined,
      latitude: toNumber(record["latitud"]),
      longitude: toNumber(record["longitud"]),
      externalCode: record["codigo_externo"]?.trim() || undefined,
      trackingCode,
      scheduledDate,
      notes: record["notas"]?.trim() || undefined,
    });
  });

  return { rows, errors };
}
