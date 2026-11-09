import axios from "axios";

const DELIVERY_SERVICE_URL = process.env.DELIVERY_SERVICE_URL || "http://localhost:8082";

export interface CustomerPayload {
  fullName: string;
  phone?: string;
  address: string;
  commune?: string;
  latitude?: number;
  longitude?: number;
  externalCode?: string;
  businessType?: string;
  declaredWindowStart?: string;
  declaredWindowEnd?: string;
}

/** Lo que el orquestador usa de la respuesta de delivery-service al crear o reutilizar un local. */
export interface UpsertedCustomer {
  id: string;
  latitude?: number | null;
  longitude?: number | null;
  /** HU-09: PROVIDED | GEOCODED | REVIEW */
  geocodeStatus?: string;
}

export interface ShipmentPayload {
  trackingCode: string;
  customerId: string;
  scheduledDate: string;
  source: string;
  notes?: string;
}

export class DeliveryServiceClient {
  constructor(private readonly bearerToken: string) {}

  private get headers() {
    return { Authorization: `Bearer ${this.bearerToken}`, "Content-Type": "application/json" };
  }

  async upsertCustomer(payload: CustomerPayload): Promise<UpsertedCustomer> {
    const { data } = await axios.post(`${DELIVERY_SERVICE_URL}/api/shipments/customers`, payload, {
      headers: this.headers,
    });
    return data;
  }

  async createShipment(payload: ShipmentPayload): Promise<{ id: string }> {
    const { data } = await axios.post(`${DELIVERY_SERVICE_URL}/api/shipments`, payload, {
      headers: this.headers,
    });
    return data;
  }
}
