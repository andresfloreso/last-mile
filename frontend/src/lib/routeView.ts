import type { BusinessType, DeclaredWindow, RouteStop, Shipment, TimeWindow } from "@/types";
import { bandForWindowStart, declaredDiffers, hourOf, type Band } from "@/lib/timeWindows";

/**
 * Una parada tal como la muestra la pantalla de rutas (HU-27). Junta, sin recalcular nada:
 * - la ruta con hora estimada (HU-14, POST /api/routes/build),
 * - el envío y su local (HU-10, GET /api/shipments): comuna, tipo, modulación, "revisar" (HU-09),
 * - la ventana real explicada (HU-11, /time-window): "X de Y visitas" y si es un local nuevo.
 */
export interface StopView {
  shipmentId: string;
  customerId: string;
  trackingCode: string;
  /** HU-30: todos los envíos de la parada (el local se visita una vez). */
  shipmentIds: string[];
  trackingCodes: string[];
  shipmentCount: number;
  /** Bultos de la parada: suma de sus envíos. */
  packageCount: number;
  position: number;
  name: string;
  address: string;
  commune: string | null;
  businessType: BusinessType | null;
  latitude: number | null;
  longitude: number | null;
  /** Hora estimada que se muestra: inicio de la entrega (llegada + espera), HH:mm. */
  eta: string;
  arrival: string;
  waitMinutes: number;
  dayOffset: number;
  outsideWindow: boolean;
  /** Ventana real con que la ruta ordena la parada (HH:mm:ss). */
  windowStart: string;
  windowEnd: string;
  band: Band;
  /** "8 de 10" dentro de la ventana; null si aún no se cargó /time-window. */
  accepted: number | null;
  visits: number | null;
  /** Sin historial: la ventana no es aprendida. */
  isNew: boolean;
  declared: DeclaredWindow | null;
  declaredDiffers: boolean;
  needsReview: boolean;
  path: [number, number][];
}

export const BUSINESS_LABEL: Record<BusinessType, string> = {
  BOTILLERIA: "Botillería",
  MINIMARKET: "Minimarket",
  BAR: "Bar",
  RESTAURANT: "Restaurant",
  OTRO: "Otro",
};

export function buildStopViews(
  route: RouteStop[],
  shipments: Shipment[],
  timeWindows: Map<string, TimeWindow>
): StopView[] {
  const byShipment = new Map(shipments.map((s) => [s.id, s]));
  return route.map((r) => {
    const s = byShipment.get(r.shipmentId);
    const shipmentIds = r.shipmentIds?.length ? r.shipmentIds : [r.shipmentId];
    const trackingCodes = shipmentIds.flatMap((id) => {
      const code = byShipment.get(id)?.trackingCode;
      return code ? [code] : [];
    });
    const customerId = s?.customer.id ?? "";
    const tw = timeWindows.get(customerId);
    const declared = s?.customer.declaredWindow ?? tw?.declaredWindow ?? null;
    const isNew = tw ? tw.realWindow.basis === "DEFAULT" : r.confidence === 0;
    // Un local nuevo se colorea por su modulación si la tiene (lo que muestra "Según modulación").
    const bandFrom = isNew && declared ? declared.start : r.windowStart;
    return {
      shipmentId: r.shipmentId,
      customerId,
      trackingCode: s?.trackingCode ?? "",
      shipmentIds,
      trackingCodes,
      shipmentCount: shipmentIds.length,
      packageCount: r.packageCount,
      position: r.position,
      name: r.customerName,
      address: r.address,
      commune: s?.customer.commune ?? null,
      businessType: s?.customer.businessType ?? null,
      latitude: r.latitude ?? null,
      longitude: r.longitude ?? null,
      eta: r.serviceStart.substring(0, 5),
      arrival: r.estimatedArrival.substring(0, 5),
      waitMinutes: r.waitMinutes,
      dayOffset: r.dayOffset,
      outsideWindow: r.outsideWindow,
      windowStart: r.windowStart,
      windowEnd: r.windowEnd,
      band: bandForWindowStart(bandFrom),
      accepted: tw ? tw.realWindow.acceptedVisits : null,
      visits: tw ? tw.realWindow.visits : null,
      isNew,
      declared,
      declaredDiffers: !isNew && declaredDiffers(declared, r.windowStart),
      needsReview: s?.customer.geocodeStatus === "REVIEW",
      path: r.path ?? [],
    };
  });
}

/**
 * Orden de la lista: primero los locales con ubicación por revisar (como en HU-09),
 * después el resto en orden de visita.
 */
export function listOrder(stops: StopView[]): StopView[] {
  return [...stops].sort((a, b) => Number(b.needsReview) - Number(a.needsReview) || a.position - b.position);
}

/** Filtro del buscador: local, dirección, comuna o código de cualquiera de sus envíos (sin tildes ni mayúsculas). */
export function matchesSearch(stop: StopView, query: string): boolean {
  const norm = (v: string) => v.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
  const q = norm(query.trim());
  if (!q) return true;
  return [stop.name, stop.address, stop.commune ?? "", stop.trackingCode, ...stop.trackingCodes].some((v) =>
    norm(v).includes(q)
  );
}

export interface RouteSummary {
  stops: number;
  km: number | null;
  endTime: string | null;
  endDayOffset: number;
  onTime: number;
}

export function summarize(stops: StopView[], route: RouteStop[]): RouteSummary {
  const last = route[route.length - 1];
  const meters = route.reduce((sum, r) => sum + (r.legDistanceMeters ?? 0), 0);
  return {
    stops: stops.length,
    km: route.length ? meters / 1000 : null,
    endTime: last ? last.estimatedDeparture.substring(0, 5) : null,
    endDayOffset: last ? last.dayOffset : 0,
    onTime: stops.filter((s) => !s.outsideWindow).length,
  };
}

/** Hora decimal de la hora estimada, contando el día siguiente (para la línea de tiempo). */
export function etaHour(stop: StopView): number {
  return hourOf(stop.eta) + 24 * stop.dayOffset;
}
