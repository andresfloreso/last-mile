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

  async upsertCustomer(payload: CustomerPayload): Promise<{ id: string }> {
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
