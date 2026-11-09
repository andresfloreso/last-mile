import { useState } from "react";
import Layout from "@/components/Layout";
import { importApi } from "@/api/endpoints";
import type { ImportRowIssue, ImportSummary } from "@/types";

export default function Import() {
  const [file, setFile] = useState<File | null>(null);
  const [summary, setSummary] = useState<ImportSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [templateError, setTemplateError] = useState<string | null>(null);

  async function handleTemplate() {
    setTemplateError(null);
    try {
      await importApi.downloadTemplate();
    } catch {
      setTemplateError("No se pudo descargar la plantilla");
    }
  }

  async function handleUpload() {
    if (!file) return;
    setLoading(true);
    setError(null);
    setSummary(null);
    try {
      const result = await importApi.uploadShipments(file);
      setSummary(result);
    } catch (err: any) {
      setError(err?.response?.data?.error || "No se pudo importar el archivo");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Layout>
      <div className="max-w-2xl mx-auto space-y-6">
        <div className="bg-panel rounded-xl shadow-sm border border-line p-6">
          <div className="flex items-start justify-between gap-4 mb-1">
            <h2 className="font-semibold text-ink">Importar planilla de envíos</h2>
            <button
              type="button"
              onClick={handleTemplate}
              className="shrink-0 rounded-md border border-primary px-3 py-1.5 text-xs font-semibold text-primary hover:bg-tile"
            >
              Descargar plantilla (.xlsx)
            </button>
          </div>
          {templateError && <p className="text-sm text-reject mb-2">{templateError}</p>}
          <p className="text-sm text-muted mb-4">
            Acepta archivos <code>.xlsx</code>, <code>.xls</code> o <code>.csv</code> con las columnas:
            <code className="block bg-tile rounded-md p-2 mt-2 text-xs">
              cliente, tipo_local, telefono, direccion, comuna, latitud, longitud, codigo_externo, modulacion_desde,
              modulacion_hasta, tracking, fecha_entrega, notas
            </code>
          </p>
          <ul className="text-xs text-muted mb-4 space-y-1 list-disc pl-5">
            <li>
              <strong>Obligatorias:</strong> cliente (nombre del local), direccion, tracking y fecha_entrega.
            </li>
            <li>
              <strong>tipo_local:</strong> botillería, minimarket, bar, restaurant u otro.
            </li>
            <li>
              <strong>Modulación:</strong> horario en que el local declara que recibe, con{" "}
              <code>modulacion_desde</code> y <code>modulacion_hasta</code> en formato HH:mm (por ejemplo 09:00 y
              12:00). LastMile aprende además su ventana real con cada intento de entrega.
            </li>
          </ul>

          <input
            type="file"
            accept=".xlsx,.xls,.csv"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="block w-full text-sm text-muted file:mr-4 file:rounded-md file:border-0 file:bg-tile file:px-4 file:py-2 file:text-sm file:font-semibold file:text-primary hover:file:bg-tile"
          />

          <button
            onClick={handleUpload}
            disabled={!file || loading}
            className="mt-4 bg-primary hover:opacity-90 text-primary-ink rounded-md px-4 py-2 text-sm font-semibold disabled:opacity-60"
          >
            {loading ? "Procesando..." : "Subir e importar"}
          </button>

          {error && <p className="text-sm text-reject mt-3">{error}</p>}
        </div>

        {summary && <ImportResult summary={summary} />}
      </div>
    </Layout>
  );
}

function Stat({ value, label, className }: { value: number; label: string; className: string }) {
  return (
    <div className={`rounded-md p-3 ${className}`}>
      <div className="text-2xl font-bold">{value}</div>
      <div className="text-xs text-muted">{label}</div>
    </div>
  );
}

type IssueKind = "error" | "skipped" | "review";

const ISSUE_BADGE: Record<IssueKind, { label: string; className: string }> = {
  error: { label: "Error", className: "bg-tile text-reject" },
  skipped: { label: "Omitido", className: "bg-warning-bg text-warning-ink" },
  review: { label: "Revisar", className: "bg-band-night-bg text-band-night-ink" },
};

/**
 * Resumen de la importación (HU-08): totales y, por fila, qué se omitió o
 * falló y por qué. HU-09: también las filas cuyo local quedó "por revisar"
 * porque la geocodificación fue aproximada o no encontró la dirección.
 */
function ImportResult({ summary }: { summary: ImportSummary }) {
  const issues: (ImportRowIssue & { kind: IssueKind })[] = [
    ...summary.errors.map((e) => ({ ...e, kind: "error" as const })),
    ...summary.skippedRows.map((e) => ({ ...e, kind: "skipped" as const })),
    ...(summary.reviewRows ?? []).map((e) => ({ ...e, kind: "review" as const })),
  ].sort((a, b) => a.rowNumber - b.rowNumber);

  return (
    <div className="bg-panel rounded-xl shadow-sm border border-line p-6">
      <h3 className="font-semibold text-ink mb-3">Resultado de la importación</h3>
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 text-center mb-4">
        <Stat value={summary.totalRows} label="Filas leídas" className="bg-tile text-ink" />
        <Stat value={summary.created} label="Envíos creados" className="bg-band-midday-bg text-band-midday-ink" />
        <Stat value={summary.skipped} label="Omitidos (ya existían)" className="bg-warning-bg text-warning-ink" />
        <Stat value={summary.failed} label="Con error" className="bg-tile text-reject" />
        <Stat value={summary.toReview ?? 0} label="Ubicación por revisar" className="bg-band-night-bg text-band-night-ink" />
      </div>

      {issues.length === 0 ? (
        <p className="text-sm text-band-midday-ink">Todas las filas se importaron sin problemas.</p>
      ) : (
        <>
          <p className="text-xs text-muted mb-2">
            Corrige las filas con error en la planilla y vuelve a importarla: las filas ya creadas se omiten, no se
            duplican. Las marcadas "Revisar" se crearon, pero su ubicación es aproximada: verifícalas en el mapa o
            agrega latitud y longitud a la fila.
          </p>
          <div className="max-h-72 overflow-y-auto border border-line rounded-md">
            <table className="w-full text-xs">
              <thead className="bg-tile text-muted sticky top-0">
                <tr>
                  <th className="text-left font-semibold px-3 py-2 w-14">Fila</th>
                  <th className="text-left font-semibold px-3 py-2 w-28">Tracking</th>
                  <th className="text-left font-semibold px-3 py-2 w-24">Estado</th>
                  <th className="text-left font-semibold px-3 py-2">Motivo</th>
                </tr>
              </thead>
              <tbody>
                {issues.map((issue) => (
                  <tr key={`${issue.kind}-${issue.rowNumber}`} className="border-t border-line">
                    <td className="px-3 py-2 font-mono">{issue.rowNumber}</td>
                    <td className="px-3 py-2 font-mono text-muted">{issue.trackingCode ?? "—"}</td>
                    <td className="px-3 py-2">
                      <span className={`rounded px-2 py-0.5 font-semibold ${ISSUE_BADGE[issue.kind].className}`}>
                        {ISSUE_BADGE[issue.kind].label}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-ink">{issue.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
