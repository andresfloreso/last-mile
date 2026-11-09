import ExcelJS from "exceljs";
import { describe, expect, it } from "vitest";
import { parseShipmentsCsv } from "./csvParser";
import { parseShipmentsExcel } from "./excelParser";

/**
 * HU-04: pruebas base de los parsers CSV y Excel (columnas obligatorias,
 * filas incompletas, fechas, coordenadas y filas vacías). Las columnas de
 * tipo de local y modulación se prueban en parsers.test.ts (HU-25).
 */

const HEADER = "cliente,direccion,comuna,latitud,longitud,codigo_externo,tracking,fecha_entrega,notas";

async function xlsx(rows: unknown[][]): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("Envíos");
  rows.forEach((r) => sheet.addRow(r));
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

describe("parseShipmentsCsv", () => {
  it("lee una fila completa con coordenadas y fecha ISO", () => {
    const { rows, errors } = parseShipmentsCsv(
      `${HEADER}\nBotillería Sol,Av. Matta 100,Santiago,-33.4572,-70.6411,LOC-1,PED-1,2026-12-01,Tocar timbre\n`
    );

    expect(errors).toEqual([]);
    expect(rows).toEqual([
      {
        rowNumber: 2,
        customerName: "Botillería Sol",
        address: "Av. Matta 100",
        commune: "Santiago",
        latitude: -33.4572,
        longitude: -70.6411,
        externalCode: "LOC-1",
        trackingCode: "PED-1",
        scheduledDate: "2026-12-01",
        notes: "Tocar timbre",
        phone: undefined,
        businessType: undefined,
      },
    ]);
  });

  it("acepta fechas dd-mm-aaaa y dd/mm/aaaa y coordenadas con coma decimal", () => {
    const { rows } = parseShipmentsCsv(
      `cliente;direccion;latitud;longitud;tracking;fecha_entrega\n` +
        `A;Calle 1;"-33,45";"-70,66";P1;01-12-2026\n` +
        `B;Calle 2;;;P2;02/12/2026\n`
    );

    expect(rows.map((r) => r.scheduledDate)).toEqual(["2026-12-01", "2026-12-02"]);
    expect(rows[0]).toMatchObject({ latitude: -33.45, longitude: -70.66 });
    expect(rows[1].latitude).toBeUndefined();
  });

  it("informa columnas obligatorias faltantes como error de archivo (fila 1)", () => {
    const result = parseShipmentsCsv("cliente,direccion\nA,Calle 1\n");

    expect(result.rows).toEqual([]);
    expect(result.errors).toEqual([{ rowNumber: 1, message: "Faltan columnas obligatorias: tracking, fecha_entrega" }]);
  });

  it("marca la fila incompleta o con fecha inválida y sigue con las demás", () => {
    const { rows, errors } = parseShipmentsCsv(
      `${HEADER}\n` +
        `A,Calle 1,,,,,P1,2026-12-01,\n` +
        `,Calle 2,,,,,P2,2026-12-01,\n` +
        `C,Calle 3,,,,,P3,mañana,\n` +
        `D,Calle 4,,,,,P4,2026-12-04,\n`
    );

    expect(rows.map((r) => r.trackingCode)).toEqual(["P1", "P4"]);
    expect(errors.map((e) => e.rowNumber)).toEqual([3, 4]);
    expect(errors[0].message).toContain("Faltan datos obligatorios");
  });

  it("ignora las filas vacías y no las cuenta como error", () => {
    const { rows, errors } = parseShipmentsCsv(`${HEADER}\n,,,,,,,,\nA,Calle 1,,,,,P1,2026-12-01,\n`);

    expect(errors).toEqual([]);
    expect(rows).toHaveLength(1);
  });

  it("una coordenada no numérica se ignora en vez de romper la fila", () => {
    const { rows } = parseShipmentsCsv(`${HEADER}\nA,Calle 1,,abc,-70.6,,P1,2026-12-01,\n`);

    expect(rows[0].latitude).toBeUndefined();
    expect(rows[0].longitude).toBe(-70.6);
  });
});

describe("parseShipmentsExcel", () => {
  it("lee filas con fechas como Date de Excel o como texto", async () => {
    const buffer = await xlsx([
      ["Cliente", "Dirección", "Comuna", "Latitud", "Longitud", "Tracking", "Fecha entrega"],
      ["Bar Uno", "Calle 1", "Ñuñoa", -33.45, -70.6, "PED-1", new Date(Date.UTC(2026, 11, 1))],
      ["Bar Dos", "Calle 2", "Ñuñoa", null, null, "PED-2", "02-12-2026"],
    ]);

    const { rows, errors } = await parseShipmentsExcel(buffer);

    expect(errors).toEqual([]);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toMatchObject({
      rowNumber: 2,
      customerName: "Bar Uno",
      commune: "Ñuñoa",
      latitude: -33.45,
      longitude: -70.6,
      trackingCode: "PED-1",
      scheduledDate: "2026-12-01",
    });
    expect(rows[1]).toMatchObject({ scheduledDate: "2026-12-02", latitude: undefined });
  });

  it("informa columnas obligatorias faltantes como error de archivo", async () => {
    const buffer = await xlsx([["cliente", "direccion", "tracking"], ["A", "B", "C"]]);

    const result = await parseShipmentsExcel(buffer);

    expect(result.rows).toEqual([]);
    expect(result.errors).toEqual([{ rowNumber: 1, message: "Faltan columnas obligatorias: fecha_entrega" }]);
  });

  it("marca la fila incompleta con su número de fila y procesa el resto", async () => {
    const buffer = await xlsx([
      ["cliente", "direccion", "tracking", "fecha_entrega"],
      ["A", "Calle 1", "P1", "2026-12-01"],
      ["B", "Calle 2", null, "2026-12-01"],
      [],
      ["D", "Calle 4", "P4", "2026-12-04"],
    ]);

    const { rows, errors } = await parseShipmentsExcel(buffer);

    expect(rows.map((r) => r.rowNumber)).toEqual([2, 5]);
    expect(errors).toHaveLength(1);
    expect(errors[0].rowNumber).toBe(3);
  });

  it("un libro sin hojas es un error de archivo", async () => {
    const empty = Buffer.from(await new ExcelJS.Workbook().xlsx.writeBuffer());

    const result = await parseShipmentsExcel(empty);

    expect(result.rows).toEqual([]);
    expect(result.errors[0].message).toBe("El archivo no tiene hojas");
  });
});
