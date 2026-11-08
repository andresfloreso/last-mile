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
}

/** Parada tal como la ve/edita el usuario antes de optimizar — igual al panel de detalle de la app de referencia. */
export interface DraftStop {
  tempId: string; // id local mientras no existe en el backend
  shipmentId?: string; // id real una vez creada
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
}

export interface GeocodeResult {
  address: string;
  latitude: number;
  longitude: number;
  confidence: number;
}

export const STOP_LABELS: { name: string; color: string }[] = [
  { name: "Default", color: "#3b82f6" },
  { name: "Afternoon Pickup", color: "#a855f7" },
  { name: "Prioridad", color: "#10b981" },
  { name: "Frágil", color: "#f59e0b" },
  { name: "Urgente", color: "#ef4444" },
];

export interface ImportSummary {
  totalRows: number;
  created: number;
  skipped: number;
  errors: string[];
}
