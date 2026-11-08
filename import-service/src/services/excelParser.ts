import ExcelJS from "exceljs";
import { ParseResult, ParsedShipmentRow, RowError } from "../types/shipment";

/**
 * Columnas esperadas en la planilla (fila 1 = encabezados, sin distinguir
 * mayúsculas/acentos exactos gracias a normalizeHeader):
 *
 *  cliente | telefono | direccion | comuna | latitud | longitud |
 *  codigo_externo | tracking | fecha_entrega (yyyy-mm-dd) | notas
 */
const REQUIRED_HEADERS = ["cliente", "direccion", "tracking", "fecha_entrega"];

function normalizeHeader(raw: string): string {
  return raw
    .toString()
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "") // quita tildes
    .replace(/\s+/g, "_");
}

function cellToString(value: ExcelJS.CellValue): string | undefined {
  if (value === null || value === undefined) return undefined;
  if (typeof value === "object" && "text" in (value as any)) return (value as any).text?.toString().trim();
  return value.toString().trim();
}

function cellToNumber(value: ExcelJS.CellValue): number | undefined {
  const str = cellToString(value);
  if (!str) return undefined;
  const num = Number(str.replace(",", "."));
  return Number.isFinite(num) ? num : undefined;
}

function cellToDateIso(value: ExcelJS.CellValue): string | undefined {
  if (value instanceof Date) {
    return value.toISOString().substring(0, 10);
  }
  const str = cellToString(value);
  if (!str) return undefined;
  // admite yyyy-mm-dd o dd-mm-yyyy / dd/mm/yyyy
  const isoMatch = str.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (isoMatch) return str;
  const dmyMatch = str.match(/^(\d{2})[/-](\d{2})[/-](\d{4})$/);
  if (dmyMatch) {
    const [, d, m, y] = dmyMatch;
    return `${y}-${m}-${d}`;
  }
  return undefined;
}

export async function parseShipmentsExcel(buffer: Buffer): Promise<ParseResult> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(buffer as any);

  const worksheet = workbook.worksheets[0];
  if (!worksheet) {
    return { rows: [], errors: [{ rowNumber: 0, message: "El archivo no tiene hojas" }] };
  }

  const headerRow = worksheet.getRow(1);
  const headerIndex: Record<string, number> = {};
  headerRow.eachCell((cell, colNumber) => {
    const header = normalizeHeader(cellToString(cell.value) || "");
    if (header) headerIndex[header] = colNumber;
  });

  const missing = REQUIRED_HEADERS.filter((h) => !(h in headerIndex));
  if (missing.length > 0) {
    return {
      rows: [],
      errors: [{ rowNumber: 1, message: `Faltan columnas obligatorias: ${missing.join(", ")}` }],
    };
  }

  const rows: ParsedShipmentRow[] = [];
  const errors: RowError[] = [];

  worksheet.eachRow((row, rowNumber) => {
    if (rowNumber === 1) return; // encabezado

    const get = (key: string) => (headerIndex[key] ? row.getCell(headerIndex[key]).value : undefined);

    const customerName = cellToString(get("cliente"));
    const address = cellToString(get("direccion"));
    const trackingCode = cellToString(get("tracking"));
    const scheduledDate = cellToDateIso(get("fecha_entrega"));

    // fila completamente vacía -> se ignora silenciosamente
    if (!customerName && !address && !trackingCode) return;

    if (!customerName || !address || !trackingCode || !scheduledDate) {
      errors.push({
        rowNumber,
        message: "Faltan datos obligatorios (cliente, direccion, tracking o fecha_entrega inválida)",
      });
      return;
    }

    rows.push({
      rowNumber,
      customerName,
      phone: cellToString(get("telefono")),
      address,
      commune: cellToString(get("comuna")),
      latitude: cellToNumber(get("latitud")),
      longitude: cellToNumber(get("longitud")),
      externalCode: cellToString(get("codigo_externo")),
      trackingCode,
      scheduledDate,
      notes: cellToString(get("notas")),
    });
  });

  return { rows, errors };
}
