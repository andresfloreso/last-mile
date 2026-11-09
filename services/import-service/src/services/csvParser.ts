import Papa from "papaparse";
import { ParseResult, ParsedShipmentRow, RowError } from "../types/shipment";
import { REQUIRED_HEADERS, normalizeHeader, parseBusinessType, parseModulation } from "./columns";

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

    const businessType = parseBusinessType(record["tipo_local"]);
    const modulation = parseModulation(record["modulacion_desde"], record["modulacion_hasta"]);
    if (!businessType.ok || !modulation.ok) {
      errors.push({ rowNumber, message: !businessType.ok ? businessType.error : (modulation as { error: string }).error });
      return;
    }

    rows.push({
      rowNumber,
      customerName,
      businessType: businessType.value,
      ...modulation.value,
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
