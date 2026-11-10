import { describe, expect, it } from "vitest";
import { ParsedShipmentRow } from "../types/shipment";
import { DeliveryApi, importShipmentRows } from "./importOrchestrator";
import { CustomerPayload, ShipmentPayload } from "./deliveryServiceClient";

function httpError(status: number, message?: string) {
  return Object.assign(new Error(`HTTP ${status}`), { response: { status, data: message ? { message } : {} } });
}

/** delivery-service en memoria: reutiliza locales por externalCode y responde 409 si el tracking existe. */
class FakeDeliveryApi implements DeliveryApi {
  customers = new Map<string, CustomerPayload & { id: string }>();
  shipments = new Map<string, ShipmentPayload>();

  async upsertCustomer(payload: CustomerPayload) {
    const key = payload.externalCode ?? `${payload.fullName}|${payload.address}`;
    if (!this.customers.has(key)) this.customers.set(key, { ...payload, id: `c-${this.customers.size + 1}` });
    return { id: this.customers.get(key)!.id };
  }

  async createShipment(payload: ShipmentPayload) {
    if (this.shipments.has(payload.trackingCode)) {
      throw httpError(409, `Ya existe un envío con el trackingCode ${payload.trackingCode}`);
    }
    this.shipments.set(payload.trackingCode, payload);
    return { id: `s-${this.shipments.size}` };
  }
}

function row(n: number, extra: Partial<ParsedShipmentRow> = {}): ParsedShipmentRow {
  return {
    rowNumber: n,
    customerName: `Local ${n}`,
    address: `Calle ${n}`,
    externalCode: `LOC-${n}`,
    trackingCode: `PED-${n}`,
    scheduledDate: "2026-12-01",
    ...extra,
  };
}

describe("importShipmentRows (HU-08)", () => {
  it("resume creados, omitidos y errores con fila y motivo", async () => {
    const api = new FakeDeliveryApi();
    api.shipments.set("PED-3", { trackingCode: "PED-3", customerId: "x", scheduledDate: "2026-12-01", source: "otro" });
    const failing: DeliveryApi = {
      upsertCustomer: (p) => api.upsertCustomer(p),
      createShipment: (p) =>
        p.trackingCode === "PED-4"
          ? Promise.reject(httpError(400, "customerId no es un identificador válido: x"))
          : p.trackingCode === "PED-5"
            ? Promise.reject(new Error("connect ECONNREFUSED"))
            : api.createShipment(p),
    };

    const summary = await importShipmentRows(
      [row(2), row(3), row(4), row(5)],
      [{ rowNumber: 6, message: "Faltan datos obligatorios" }],
      failing,
      "import:test.xlsx"
    );

    expect(summary).toEqual({
      totalRows: 5,
      created: 1,
      skipped: 1,
      failed: 3,
      toReview: 0,
      skippedRows: [{ rowNumber: 3, trackingCode: "PED-3", message: "Ya existe un envío con el trackingCode PED-3" }],
      errors: [
        { rowNumber: 4, trackingCode: "PED-4", message: "customerId no es un identificador válido: x" },
        { rowNumber: 5, trackingCode: "PED-5", message: "No se pudo contactar a delivery-service" },
        { rowNumber: 6, message: "Faltan datos obligatorios" },
      ],
      reviewRows: [],
    });
  });

  it("importar dos veces la misma planilla no duplica locales ni envíos", async () => {
    const api = new FakeDeliveryApi();
    const rows = [row(2), row(3), row(4, { externalCode: "LOC-2" })]; // dos envíos del mismo local

    const first = await importShipmentRows(rows, [], api, "import:a.xlsx");
    const second = await importShipmentRows(rows, [], api, "import:a.xlsx");

    expect(first).toMatchObject({ created: 3, skipped: 0, failed: 0 });
    expect(second).toMatchObject({ created: 0, skipped: 3, failed: 0 });
    expect(api.customers.size).toBe(2);
    expect(api.shipments.size).toBe(3);
  });

  it("un error sin mensaje informa el código HTTP", async () => {
    const api: DeliveryApi = {
      upsertCustomer: () => Promise.reject(httpError(503)),
      createShipment: () => Promise.resolve({ id: "s" }),
    };
    const summary = await importShipmentRows([row(2)], [], api, "x");
    expect(summary.errors).toEqual([{ rowNumber: 2, trackingCode: "PED-2", message: "delivery-service respondió 503" }]);
  });

  it("informa los locales que quedaron por revisar al geocodificar (HU-09)", async () => {
    const api = new FakeDeliveryApi();
    const geocoding: DeliveryApi = {
      upsertCustomer: async (p) => {
        const { id } = await api.upsertCustomer(p);
        if (p.externalCode === "LOC-3") return { id, geocodeStatus: "REVIEW", latitude: -33.45, longitude: -70.6 };
        if (p.externalCode === "LOC-4") return { id, geocodeStatus: "REVIEW", latitude: null, longitude: null };
        return { id, geocodeStatus: "GEOCODED", latitude: -33.4, longitude: -70.5 };
      },
      createShipment: (p) => (p.trackingCode === "PED-5" ? Promise.reject(httpError(400, "inválido")) : api.createShipment(p)),
    };

    const summary = await importShipmentRows(
      [row(2), row(3), row(4), row(5, { externalCode: "LOC-3" })],
      [],
      geocoding,
      "x"
    );

    expect(summary).toMatchObject({ created: 3, failed: 1, toReview: 2 });
    expect(summary.reviewRows).toEqual([
      { rowNumber: 3, trackingCode: "PED-3", message: "Ubicación aproximada: revisa el local en el mapa" },
      {
        rowNumber: 4,
        trackingCode: "PED-4",
        message: "Dirección no encontrada: el local quedó sin ubicación, agrega latitud y longitud",
      },
    ]);
  });

  it("envía el peso y el volumen de la fila a delivery-service (HU-29)", async () => {
    const api = new FakeDeliveryApi();
    await importShipmentRows([row(2, { weightKg: 24.5, volumeM3: 0.08 }), row(3)], [], api, "x");
    expect(api.shipments.get("PED-2")).toMatchObject({ weightKg: 24.5, volumeM3: 0.08 });
    expect(api.shipments.get("PED-3")?.weightKg).toBeUndefined();
  });
});
