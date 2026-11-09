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

export interface ImportSummary {
  totalRows: number;
  created: number;
  skipped: number;
  errors: string[];
}
