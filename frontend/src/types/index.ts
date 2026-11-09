export interface AuthUser {
  id: string;
  fullName: string;
  email: string;
  role: "ADMIN" | "DISPATCHER" | "DRIVER" | "OPERATOR" | "CLIENT";
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
  user: AuthUser;
}

export type StopType = "DELIVERY" | "PICKUP";
export type OrderPreference = "FIRST" | "AUTO" | "LAST";

export interface RouteStop {
  shipmentId: string;
  customerName: string;
  address: string;
  position: number;
  windowStart: string; // HH:mm:ss
  windowEnd: string;
  confidence: number; // 0..1
  hoursToAvoid: number[];
  stopType: StopType;
  packageCount: number;
  orderPreference: OrderPreference;
  label?: string;
  latitude?: number;
  longitude?: number;
  // ---- HU-14: hora estimada por parada ----
  /** Llegada estimada del camión (HH:mm). */
  estimatedArrival: string;
  /** Inicio de la entrega: la llegada, o la apertura de la ventana real si llega antes (HH:mm). */
  serviceStart: string;
  /** Salida del local (HH:mm). */
  estimatedDeparture: string;
  /** Minutos esperando a que abra la ventana real. */
  waitMinutes: number;
  /** Minutos de viaje desde la parada anterior; null si el local no tiene coordenadas. */
  travelMinutes: number | null;
  legDistanceMeters: number | null;
  /** La entrega empezaría cuando la ventana real ya cerró. */
  outsideWindow: boolean;
  /** Tramo desde la parada anterior, como pares [lat, lng]. */
  path: [number, number][];
  timingSource: "OSRM" | "ESTIMATED" | "NO_LOCATION";
  /** 1 si la ruta no alcanza en el día y la llegada es al día siguiente. */
  dayOffset: number;
}

/** Parada tal como la ve/edita el usuario antes de optimizar — igual al panel de detalle de la app de referencia. */
export interface DraftStop {
  tempId: string; // id local mientras no existe en el backend
  shipmentId?: string; // id real una vez creada
  /** Id del local, cuando la parada viene de un envío existente (HU-11: detalle del local). */
  customerId?: string;
  customerName: string;
  address: string;
  commune?: string;
  phone?: string;
  latitude?: number;
  longitude?: number;
  stopType: StopType;
  packageCount: number;
  orderPreference: OrderPreference;
  label: string;
  notes: string;
  /** HU-09: la ubicación se geocodificó de forma aproximada; el mapa la marca "revisar". */
  needsReview?: boolean;
}

export interface GeocodeResult {
  address: string;
  latitude: number;
  longitude: number;
  confidence: number;
  /** HU-09: Nominatim no ubicó el número de calle; el pin es aproximado. */
  needsReview?: boolean;
}

export const STOP_LABELS: { name: string; color: string }[] = [
  { name: "Default", color: "#3b82f6" },
  { name: "Afternoon Pickup", color: "#a855f7" },
  { name: "Prioridad", color: "#10b981" },
  { name: "Frágil", color: "#f59e0b" },
  { name: "Urgente", color: "#ef4444" },
];

/** Fila con un problema en el resumen de importación (HU-08). */
export interface ImportRowIssue {
  rowNumber: number;
  trackingCode?: string;
  message: string;
}

export interface ImportSummary {
  totalRows: number;
  created: number;
  /** Filas cuyo envío ya existía (por ejemplo, al reimportar la misma planilla). */
  skipped: number;
  failed: number;
  /** Locales con ubicación aproximada o no encontrada al geocodificar (HU-09). */
  toReview: number;
  skippedRows: ImportRowIssue[];
  errors: ImportRowIssue[];
  reviewRows: ImportRowIssue[];
}

/** Origen de las coordenadas de un local (HU-09). REVIEW = aproximada o no encontrada: revisar. */
export type GeocodeStatus = "PROVIDED" | "GEOCODED" | "REVIEW";

/** Estados de un envío en delivery-service. */
export type ShipmentStatus = "PENDING" | "ROUTED" | "OUT_FOR_DELIVERY" | "DELIVERED" | "REJECTED" | "RESCHEDULED";

export type BusinessType = "BOTILLERIA" | "MINIMARKET" | "BAR" | "RESTAURANT" | "OTRO";

/** Una página de resultados (GET /api/shipments, HU-10). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Envío tal como lo devuelve GET /api/shipments, con su local embebido. */
export interface Shipment {
  id: string;
  trackingCode: string;
  scheduledDate: string;
  status: ShipmentStatus;
  source?: string | null;
  notes?: string | null;
  routePosition?: number | null;
  stopType: StopType;
  packageCount: number;
  orderPreference: OrderPreference;
  label?: string | null;
  createdAt: string;
  customer: {
    id: string;
    fullName: string;
    phone?: string | null;
    address: string;
    commune?: string | null;
    latitude?: number | null;
    longitude?: number | null;
    externalCode?: string | null;
    businessType?: BusinessType | null;
    declaredWindow?: DeclaredWindow | null;
    geocodeStatus?: GeocodeStatus;
  };
}

/** Filtros de GET /api/shipments. Todos opcionales. */
export interface ShipmentQuery {
  date?: string;
  from?: string;
  to?: string;
  status?: ShipmentStatus[];
  commune?: string;
  q?: string;
  page?: number;
  size?: number;
}

/** Modulación declarada por el local (HU-25). Horas en formato HH:mm. */
export interface DeclaredWindow {
  start: string;
  end: string;
  start2?: string | null;
  end2?: string | null;
}

/** GET /api/customers/{id}/time-window (HU-11): modulación vs. ventana real. */
export interface TimeWindow {
  customerId: string;
  customerName: string;
  declaredWindow: DeclaredWindow | null;
  realWindow: {
    start: string;
    end: string;
    /** La X de "X de Y visitas". */
    acceptedVisits: number;
    /** La Y. */
    visits: number;
    /** LEARNED: aprendida de los intentos; DEFAULT: sin intentos, horario comercial por defecto. */
    basis: "LEARNED" | "DEFAULT";
  };
  summary: string;
  totalAttempts: number;
  acceptedAttempts: number;
  rejectedAttempts: number;
  hoursToAvoid: number[];
  /** 24 casilleros, de 0 a 23 h (hora de Santiago). */
  histogram: { hour: number; accepted: number; rejected: number }[];
}


/** GET /api/customers/{id} */
export interface Customer {
  id: string;
  fullName: string;
  phone?: string | null;
  address: string;
  commune?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  externalCode?: string | null;
  businessType?: BusinessType | null;
  declaredWindow: DeclaredWindow | null;
  geocodeStatus?: GeocodeStatus;
}
