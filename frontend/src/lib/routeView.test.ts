import { describe, expect, it } from "vitest";
import type { RouteStop, Shipment, TimeWindow } from "@/types";
import { buildStopViews, etaHour, listOrder, matchesSearch, summarize } from "./routeView";

function routeStop(over: Partial<RouteStop>): RouteStop {
  return {
    shipmentId: "s1",
    customerName: "Botillería El Roble",
    address: "Av. Providencia 2124",
    position: 1,
    windowStart: "12:00:00",
    windowEnd: "14:00:00",
    confidence: 1,
    hoursToAvoid: [],
    stopType: "DELIVERY",
    packageCount: 1,
    orderPreference: "AUTO",
    latitude: -33.42,
    longitude: -70.6,
    estimatedArrival: "11:45",
    serviceStart: "12:00",
    estimatedDeparture: "12:10",
    waitMinutes: 15,
    travelMinutes: 10,
    legDistanceMeters: 4200,
    outsideWindow: false,
    path: [],
    timingSource: "OSRM",
    dayOffset: 0,
    ...over,
  };
}

function shipment(id: string, customerId: string, over: Partial<Shipment["customer"]> = {}): Shipment {
  return {
    id,
    trackingCode: `SEED-${id}`,
    scheduledDate: "2026-11-09",
    status: "ROUTED",
    stopType: "DELIVERY",
    packageCount: 1,
    orderPreference: "AUTO",
    createdAt: "",
    customer: {
      id: customerId,
      fullName: "Local",
      address: "Calle 1",
      commune: "Providencia",
      declaredWindow: { start: "09:00", end: "12:00" },
      geocodeStatus: "GEOCODED",
      ...over,
    },
  };
}

function timeWindow(customerId: string, basis: "LEARNED" | "DEFAULT", accepted = 8, visits = 10): TimeWindow {
  return {
    customerId,
    customerName: "Local",
    declaredWindow: { start: "09:00", end: "12:00" },
    realWindow: { start: "12:00", end: "14:00", acceptedVisits: accepted, visits, basis },
    summary: "",
    totalAttempts: visits,
    acceptedAttempts: accepted,
    rejectedAttempts: visits - accepted,
    hoursToAvoid: [],
    histogram: [],
  };
}

describe("buildStopViews", () => {
  it("junta la ruta, el envío y la ventana real sin recalcular nada", () => {
    const [v] = buildStopViews([routeStop({})], [shipment("s1", "c1")], new Map([["c1", timeWindow("c1", "LEARNED")]]));
    expect(v).toMatchObject({
      customerId: "c1",
      trackingCode: "SEED-s1",
      commune: "Providencia",
      eta: "12:00", // la hora estimada suma la espera
      arrival: "11:45",
      waitMinutes: 15,
      accepted: 8,
      visits: 10,
      isNew: false,
      declaredDiffers: true, // declara 09–12, recibe desde las 12
    });
    expect(v.band.label).toBe("Mediodía");
  });

  it("un local sin historial es nuevo, toma la franja de su modulación y no marca diferencia", () => {
    const [v] = buildStopViews(
      [routeStop({ windowStart: "09:00:00", windowEnd: "18:00:00", confidence: 0 })],
      [shipment("s1", "c1", { declaredWindow: { start: "18:00", end: "21:00" } })],
      new Map([["c1", timeWindow("c1", "DEFAULT", 0, 0)]])
    );
    expect(v.isNew).toBe(true);
    expect(v.band.label).toBe("Tarde");
    expect(v.declaredDiffers).toBe(false);
  });

  it("marca la ubicación por revisar y tolera que falte /time-window", () => {
    const [v] = buildStopViews([routeStop({})], [shipment("s1", "c1", { geocodeStatus: "REVIEW" })], new Map());
    expect(v.needsReview).toBe(true);
    expect(v.accepted).toBeNull();
    expect(v.isNew).toBe(false); // confidence 1: tiene historial
  });
});

describe("lista y resumen", () => {
  const stops = buildStopViews(
    [
      routeStop({ shipmentId: "a", position: 1, customerName: "Minimarket Don Lucho" }),
      routeStop({ shipmentId: "b", position: 2, customerName: "Bar Ñuñoa", outsideWindow: true, legDistanceMeters: 800 }),
      routeStop({ shipmentId: "c", position: 3, customerName: "Café", estimatedDeparture: "00:20", dayOffset: 1 }),
    ],
    [shipment("a", "c1"), shipment("b", "c2", { geocodeStatus: "REVIEW", commune: "Ñuñoa" }), shipment("c", "c3")],
    new Map()
  );

  it("pone primero las ubicaciones por revisar y luego el orden de visita", () => {
    expect(listOrder(stops).map((s) => s.position)).toEqual([2, 1, 3]);
  });

  it("busca por local, dirección, comuna o código sin tildes", () => {
    expect(stops.filter((s) => matchesSearch(s, "nunoa")).map((s) => s.position)).toEqual([2]);
    expect(stops.filter((s) => matchesSearch(s, "seed-a")).map((s) => s.position)).toEqual([1]);
    expect(stops.filter((s) => matchesSearch(s, "  ")).length).toBe(3);
  });

  it("resume paradas, km, término y cuántas llegan dentro de su ventana", () => {
    const r = summarize(stops, [
      routeStop({ legDistanceMeters: 4200 }),
      routeStop({ legDistanceMeters: 800 }),
      routeStop({ legDistanceMeters: null, estimatedDeparture: "00:20", dayOffset: 1 }),
    ]);
    expect(r).toEqual({ stops: 3, km: 5, endTime: "00:20", endDayOffset: 1, onTime: 2 });
    expect(summarize([], [])).toEqual({ stops: 0, km: null, endTime: null, endDayOffset: 0, onTime: 0 });
  });

  it("la hora de la línea de tiempo cuenta el día siguiente", () => {
    expect(etaHour(stops[0])).toBe(12);
    expect(etaHour(stops[2])).toBe(36);
  });
});
