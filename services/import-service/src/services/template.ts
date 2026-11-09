import ExcelJS from "exceljs";
import { IMPORT_COLUMNS } from "./columns";

export const TEMPLATE_FILENAME = "plantilla-envios-lastmile.xlsx";

/**
 * Plantilla .xlsx descargable (HU-25): hoja "Envíos" con los encabezados que
 * espera el parser y una fila de ejemplo, y hoja "Instrucciones" con qué es
 * cada columna. Se arma desde IMPORT_COLUMNS, la misma lista que usan los
 * parsers.
 */
export async function buildImportTemplate(): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  workbook.creator = "LastMile";

  const sheet = workbook.addWorksheet("Envíos");
  sheet.columns = IMPORT_COLUMNS.map((c) => ({
    header: c.key,
    key: c.key,
    width: Math.max(14, c.example.length + 4),
    // Horas como texto: evita que Excel las convierta en fracciones de día.
    style: c.key.startsWith("modulacion_") ? { numFmt: "@" } : {},
  }));
  sheet.getRow(1).font = { bold: true };
  sheet.addRow(Object.fromEntries(IMPORT_COLUMNS.map((c) => [c.key, c.example])));
  sheet.views = [{ state: "frozen", ySplit: 1 }];

  const help = workbook.addWorksheet("Instrucciones");
  help.columns = [
    { header: "Columna", key: "key", width: 20 },
    { header: "Obligatoria", key: "required", width: 12 },
    { header: "Descripción", key: "description", width: 70 },
  ];
  help.getRow(1).font = { bold: true };
  for (const c of IMPORT_COLUMNS) {
    help.addRow({ key: c.key, required: c.required ? "sí" : "no", description: c.description });
  }
  help.addRow({});
  help.addRow({
    key: "Modulación",
    description:
      "Horario en que el local dice que recibe. LastMile aprende además su ventana real con los intentos de entrega.",
  });

  const arrayBuffer = await workbook.xlsx.writeBuffer();
  return Buffer.from(arrayBuffer);
}
