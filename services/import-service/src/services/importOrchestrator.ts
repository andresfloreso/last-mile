import { ImportSummary, ParsedShipmentRow, RowError, RowIssue } from "../types/shipment";
import { CustomerPayload, ShipmentPayload } from "./deliveryServiceClient";

/** Lo que el orquestador necesita de delivery-service (inyectable en tests). */
export interface DeliveryApi {
  upsertCustomer(payload: CustomerPayload): Promise<{ id: string }>;
  createShipment(payload: ShipmentPayload): Promise<{ id: string }>;
}

interface HttpLikeError {
  response?: { status?: number; data?: { message?: string } };
}

/** Mensaje legible de un error de delivery-service (sus mensajes ya son seguros: #12). */
function apiMessage(err: unknown): string {
  const response = (err as HttpLikeError)?.response;
  if (response?.data?.message) return response.data.message;
  if (response?.status) return `delivery-service respondió ${response.status}`;
  return "No se pudo contactar a delivery-service";
}

/**
 * Importa las filas válidas y arma el resumen por fila (HU-08):
 *  - creados: envío creado;
 *  - omitidos: el envío ya existía (mismo tracking: 409), por ejemplo al
 *    importar dos veces la misma planilla; el local tampoco se duplica, porque
 *    se reutiliza por codigo_externo;
 *  - errores: filas inválidas en la planilla o rechazadas por delivery-service.
 */
export async function importShipmentRows(
  rows: ParsedShipmentRow[],
  parseErrors: RowError[],
  api: DeliveryApi,
  source: string
): Promise<ImportSummary> {
  const errors: RowIssue[] = parseErrors.map((e) => ({ rowNumber: e.rowNumber, message: e.message }));
  const skippedRows: RowIssue[] = [];
  let created = 0;

  for (const row of rows) {
    try {
      const customer = await api.upsertCustomer({
        fullName: row.customerName,
        phone: row.phone,
        address: row.address,
        commune: row.commune,
        latitude: row.latitude,
        longitude: row.longitude,
        externalCode: row.externalCode,
        businessType: row.businessType,
        declaredWindowStart: row.declaredWindowStart,
        declaredWindowEnd: row.declaredWindowEnd,
      });

      await api.createShipment({
        trackingCode: row.trackingCode,
        customerId: customer.id,
        scheduledDate: row.scheduledDate,
        source,
        notes: row.notes,
      });
      created++;
    } catch (err) {
      const issue = { rowNumber: row.rowNumber, trackingCode: row.trackingCode, message: apiMessage(err) };
      if ((err as HttpLikeError)?.response?.status === 409) {
        skippedRows.push(issue);
      } else {
        errors.push(issue);
      }
    }
  }

  errors.sort((a, b) => a.rowNumber - b.rowNumber);
  return {
    totalRows: rows.length + parseErrors.length,
    created,
    skipped: skippedRows.length,
    failed: errors.length,
    skippedRows,
    errors,
  };
}
