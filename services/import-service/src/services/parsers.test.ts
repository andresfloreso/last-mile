import ExcelJS from "exceljs";
import { describe, expect, it } from "vitest";
import { IMPORT_COLUMNS } from "./columns";
import { parseShipmentsCsv } from "./csvParser";
import { parseShipmentsExcel } from "./excelParser";
import { buildImportTemplate } from "./template";

const HEADER = "cliente,tipo_local,direccion,comuna,codigo_externo,Modulación desde,Modulación hasta,tracking,fecha_entrega";

describe("parseShipmentsCsv con tipo de local y modulación (HU-25)", () => {
  it("lee tipo_local, modulacion_desde y modulacion_hasta (encabezados con tildes y espacios)", () => {
    const csv = `${HEADER}\nBar La Esquina,bar,Av. Irarrázaval 3550,Ñuñoa,LOC-4,9,12:00,PED-1,2026-12-01\n`;
    const { rows, errors } = parseShipmentsCsv(csv);

    expect(errors).toEqual([]);
    expect(rows[0]).toMatchObject({
      customerName: "Bar La Esquina",
      businessType: "BAR",
      declaredWindowStart: "09:00",
      declaredWindowEnd: "12:00",
      trackingCode: "PED-1",
    });
  });

  it("las columnas nuevas son opcionales", () => {
    const csv = "cliente,direccion,tracking,fecha_entrega\nMinimarket Don Lucho,Plaza de Armas s/n,PED-2,01-12-2026\n";
    const { rows, errors } = parseShipmentsCsv(csv);
    expect(errors).toEqual([]);
    expect(rows[0].businessType).toBeUndefined();
    expect(rows[0].declaredWindowStart).toBeUndefined();
    expect(rows[0].scheduledDate).toBe("2026-12-01");
    // HU-29: sin peso ni volumen, delivery-service aplica el valor por defecto.
    expect(rows[0].weightKg).toBeUndefined();
    expect(rows[0].volumeM3).toBeUndefined();
  });

  it("informa la fila y el motivo cuando el tipo o la modulación son inválidos", () => {
    const csv = [
      HEADER,
      "A,kiosco,Dir 1,Santiago,L1,9,12,PED-3,2026-12-01",
      "B,bar,Dir 2,Santiago,L2,15,12,PED-4,2026-12-01",
      "C,bar,Dir 3,Santiago,L3,09:00,,PED-5,2026-12-01",
      "D,minimarket,Dir 4,Santiago,L4,9,12,PED-6,2026-12-01",
    ].join("\n");
    const { rows, errors } = parseShipmentsCsv(csv);

    expect(rows.map((r) => r.trackingCode)).toEqual(["PED-6"]);
    expect(errors).toEqual([
      { rowNumber: 2, message: expect.stringContaining('tipo_local inválido: "kiosco"') },
      { rowNumber: 3, message: expect.stringContaining("debe ser anterior a modulacion_hasta") },
      { rowNumber: 4, message: "La modulación necesita modulacion_desde y modulacion_hasta" },
    ]);
  });
});

describe("peso y volumen (HU-29)", () => {
  const H = "cliente,direccion,tracking,fecha_entrega,Peso kg,Volumen m3";

  it("lee peso_kg y volumen_m3 con coma o punto decimal", () => {
    const csv = `${H}\nA,Dir 1,PED-7,2026-12-01,"24,5",0.08\nB,Dir 2,PED-8,2026-12-01,12,\n`;
    const { rows, errors } = parseShipmentsCsv(csv);
    expect(errors).toEqual([]);
    expect(rows[0]).toMatchObject({ weightKg: 24.5, volumeM3: 0.08 });
    expect(rows[1].weightKg).toBe(12);
    expect(rows[1].volumeM3).toBeUndefined();
  });

  it("un peso o volumen que no es un número positivo es error de la fila", () => {
    const csv = [H, "A,Dir 1,PED-9,2026-12-01,0,0.1", "B,Dir 2,PED-10,2026-12-01,10,-1", "C,Dir 3,PED-11,2026-12-01,diez,"].join("\n");
    const { rows, errors } = parseShipmentsCsv(csv);
    expect(rows).toEqual([]);
    expect(errors).toEqual([
      { rowNumber: 2, message: 'peso_kg debe ser un número mayor que 0: "0"' },
      { rowNumber: 3, message: 'volumen_m3 debe ser un número mayor que 0: "-1"' },
      { rowNumber: 4, message: 'peso_kg debe ser un número mayor que 0: "diez"' },
    ]);
  });

  it("en Excel acepta celdas numéricas", async () => {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet("Envíos");
    sheet.addRow(["cliente", "direccion", "tracking", "fecha_entrega", "peso_kg", "volumen_m3"]);
    sheet.addRow(["A", "Dir 1", "PED-12", "2026-12-01", 30, 0.12]);
    const { rows, errors } = await parseShipmentsExcel(Buffer.from(await workbook.xlsx.writeBuffer()));
    expect(errors).toEqual([]);
    expect(rows[0]).toMatchObject({ weightKg: 30, volumeM3: 0.12 });
  });
});

describe("parseShipmentsExcel", () => {
  it("lee horas guardadas como hora de Excel (Date) y como fracción de día", async () => {
    const workbook = new ExcelJS.Workbook();
    const sheet = workbook.addWorksheet("Envíos");
    sheet.addRow(["cliente", "tipo_local", "direccion", "modulacion_desde", "modulacion_hasta", "tracking", "fecha_entrega"]);
    sheet.addRow(["Restaurant Alameda", "Restaurante", "Alameda 3363", new Date(Date.UTC(1899, 11, 30, 15, 0)), 0.75, "PED-7", "2026-12-01"]);
    const buffer = Buffer.from(await workbook.xlsx.writeBuffer());

    const { rows, errors } = await parseShipmentsExcel(buffer);
    expect(errors).toEqual([]);
    expect(rows[0]).toMatchObject({
      businessType: "RESTAURANT",
      declaredWindowStart: "15:00",
      declaredWindowEnd: "18:00",
    });
  });
});

describe("plantilla descargable", () => {
  it("tiene todas las columnas, incluidas tipo_local y la modulación, en una hoja Envíos", async () => {
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load((await buildImportTemplate()) as unknown as ArrayBuffer);

    const headers = (workbook.getWorksheet("Envíos")!.getRow(1).values as unknown[]).slice(1);
    expect(headers).toEqual(IMPORT_COLUMNS.map((c) => c.key));
    expect(headers).toEqual(expect.arrayContaining(["tipo_local", "modulacion_desde", "modulacion_hasta", "peso_kg", "volumen_m3"]));
    expect(workbook.getWorksheet("Instrucciones")).toBeDefined();
  });

  it("la fila de ejemplo de la plantilla se importa sin errores", async () => {
    const { rows, errors } = await parseShipmentsExcel(await buildImportTemplate());
    expect(errors).toEqual([]);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      customerName: "Botillería El Roble",
      businessType: "BOTILLERIA",
      declaredWindowStart: "09:00",
      declaredWindowEnd: "12:00",
      weightKg: 24.5,
      volumeM3: 0.08,
    });
  });
});
