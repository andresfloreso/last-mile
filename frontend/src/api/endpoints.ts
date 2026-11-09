import { apiClient } from "./client";
import type {
  AuthResponse,
  Customer,
  DraftStop,
  GeocodeResult,
  ImportSummary,
  Page,
  RouteStop,
  Shipment,
  ShipmentQuery,
  TimeWindow,
} from "@/types";

export const authApi = {
  // Sin registro: las cuentas las crea un ADMIN (#44).
  login: (email: string, password: string) =>
    apiClient.post<AuthResponse>("/api/auth/login", { email, password }).then((r) => r.data),
};

export const routesApi = {
  build: (date: string, depotLat: number, depotLng: number) =>
    apiClient.post<RouteStop[]>("/api/routes/build", { date, depotLat, depotLng }).then((r) => r.data),
};

export const importApi = {
  uploadShipments: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiClient
      .post<ImportSummary>("/api/import/shipments", form, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((r) => r.data);
  },

  /** Descarga la plantilla .xlsx con las columnas esperadas (incluye tipo de local y modulación). */
  downloadTemplate: async () => {
    const { data } = await apiClient.get<Blob>("/api/import/template", { responseType: "blob" });
    const url = URL.createObjectURL(data);
    const link = document.createElement("a");
    link.href = url;
    link.download = "plantilla-envios-lastmile.xlsx";
    link.click();
    URL.revokeObjectURL(url);
  },
};

export const trackingApi = {
  track: (trackingCode: string) =>
    apiClient.get(`/api/tracking/${trackingCode}`).then((r) => r.data),
};

export const geocodingApi = {
  /** Geocodificación precisa vía Nominatim (delivery-service) para el buscador de direcciones. */
  search: (query: string, commune?: string) =>
    apiClient
      .get<GeocodeResult[]>("/api/geocoding/search", { params: { q: query, commune } })
      .then((r) => r.data),
};

export const shipmentsApi = {
  /** Crea (o reutiliza) el cliente y luego el envío — usado al confirmar una parada del panel. */
  createStop: async (stop: DraftStop, scheduledDate: string) => {
    const customer = await apiClient
      .post<{ id: string }>("/api/shipments/customers", {
        fullName: stop.customerName,
        phone: stop.phone,
        address: stop.address,
        commune: stop.commune,
        latitude: stop.latitude,
        longitude: stop.longitude,
      })
      .then((r) => r.data);

    const shipment = await apiClient
      .post<{ id: string }>("/api/shipments", {
        trackingCode: `RT-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
        customerId: customer.id,
        scheduledDate,
        source: "manual",
        notes: stop.notes,
        stopType: stop.stopType,
        packageCount: stop.packageCount,
        orderPreference: stop.orderPreference,
        label: stop.label,
      })
      .then((r) => r.data);

    return shipment;
  },

  update: (shipmentId: string, patch: Partial<Pick<DraftStop, "stopType" | "packageCount" | "orderPreference" | "label" | "notes">>) =>
    apiClient.patch(`/api/shipments/${shipmentId}`, patch).then((r) => r.data),

  duplicate: (shipmentId: string) =>
    apiClient
      .post(`/api/shipments/${shipmentId}/duplicate`, null, {
        params: { newTrackingCode: `RT-${Date.now()}-${Math.floor(Math.random() * 1000)}` },
      })
      .then((r) => r.data),

  remove: (shipmentId: string) => apiClient.delete(`/api/shipments/${shipmentId}`),

  /** Listado paginado con filtros (HU-10). Los estados viajan como status=A&status=B. */
  list: (query: ShipmentQuery) =>
    apiClient
      .get<Page<Shipment>>("/api/shipments", { params: query, paramsSerializer: { indexes: null } })
      .then((r) => r.data),

  /** Todos los envíos de un día, para armar la ruta (el backend admite hasta 500 por página). */
  listByDate: (date: string) => shipmentsApi.list({ date, size: 500 }).then((p) => p.content),
};

export const customersApi = {
  get: (customerId: string) => apiClient.get<Customer>(`/api/customers/${customerId}`).then((r) => r.data),

  /** Modulación declarada vs. ventana real aprendida (HU-11). */
  timeWindow: (customerId: string) =>
    apiClient.get<TimeWindow>(`/api/customers/${customerId}/time-window`).then((r) => r.data),
};
