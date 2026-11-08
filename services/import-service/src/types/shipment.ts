export interface ParsedShipmentRow {
  rowNumber: number;
  customerName: string;
  phone?: string;
  address: string;
  commune?: string;
  latitude?: number;
  longitude?: number;
  externalCode?: string;
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
