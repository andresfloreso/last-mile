import { ParsedShipmentRow, ImportSummary, RowError } from "../types/shipment";
import { DeliveryServiceClient } from "./deliveryServiceClient";

export async function importShipmentRows(
  rows: ParsedShipmentRow[],
  parseErrors: RowError[],
  bearerToken: string,
  source: string
): Promise<ImportSummary> {
  const client = new DeliveryServiceClient(bearerToken);
  const errors: string[] = parseErrors.map((e) => `Fila ${e.rowNumber}: ${e.message}`);

  let created = 0;
  let skipped = 0;

  for (const row of rows) {
    try {
      const customer = await client.upsertCustomer({
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

      await client.createShipment({
        trackingCode: row.trackingCode,
        customerId: customer.id,
        scheduledDate: row.scheduledDate,
        source,
        notes: row.notes,
      });

      created++;
    } catch (err: any) {
      skipped++;
      const apiMessage = err?.response?.data?.message || err.message;
      errors.push(`Fila ${row.rowNumber} (${row.trackingCode}): ${apiMessage}`);
    }
  }

  return {
    totalRows: rows.length + parseErrors.length,
    created,
    skipped,
    errors,
  };
}
