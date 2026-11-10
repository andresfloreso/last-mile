export interface ParsedShipmentRow {
  rowNumber: number;
  customerName: string;
  phone?: string;
  address: string;
  commune?: string;
  latitude?: number;
  longitude?: number;
  externalCode?: string;
  /** Tipo de local (HU-25): BOTILLERIA | MINIMARKET | BAR | RESTAURANT | OTRO */
  businessType?: string;
  /** Modulación (HU-25): horario de recepción declarado, "HH:mm" */
  declaredWindowStart?: string;
  declaredWindowEnd?: string;
  trackingCode: string;
  scheduledDate: string; // ISO yyyy-MM-dd
  /** Peso del envío en kg (HU-29); si falta, delivery-service usa el valor por defecto. */
  weightKg?: number;
  /** Volumen del envío en m³ (HU-29); si falta, delivery-service usa el valor por defecto. */
  volumeM3?: number;
  notes?: string;
}

export interface RowError {
  rowNumber: number;
  message: string;
}

export interface ParseResult {
  rows: ParsedShipmentRow[];
  errors: RowError[];
}

/** Fila con un problema en el resumen de importación (HU-08). */
export interface RowIssue {
  /** Número de fila en la planilla (la fila 1 es el encabezado). */
  rowNumber: number;
  trackingCode?: string;
  message: string;
}

export interface ImportSummary {
  /** Filas con datos leídas de la planilla. */
  totalRows: number;
  /** Envíos creados. */
  created: number;
  /** Filas omitidas porque el envío ya existía (por ejemplo, al reimportar la misma planilla). */
  skipped: number;
  /** Filas con error (datos inválidos o rechazadas por delivery-service). */
  failed: number;
  /** Locales con ubicación aproximada o no encontrada al geocodificar (HU-09). */
  toReview: number;
  skippedRows: RowIssue[];
  errors: RowIssue[];
  reviewRows: RowIssue[];
}
