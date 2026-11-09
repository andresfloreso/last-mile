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
        <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
          <div className="flex items-start justify-between gap-4 mb-1">
            <h2 className="font-semibold text-slate-800">Importar planilla de envíos</h2>
            <button
              type="button"
              onClick={handleTemplate}
              className="shrink-0 rounded-md border border-brand-600 px-3 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-50"
            >
              Descargar plantilla (.xlsx)
            </button>
          </div>
          {templateError && <p className="text-sm text-red-600 mb-2">{templateError}</p>}
          <p className="text-sm text-slate-500 mb-4">
            Acepta archivos <code>.xlsx</code>, <code>.xls</code> o <code>.csv</code> con las columnas:
            <code className="block bg-slate-50 rounded-md p-2 mt-2 text-xs">
              cliente, tipo_local, telefono, direccion, comuna, latitud, longitud, codigo_externo, modulacion_desde,
              modulacion_hasta, tracking, fecha_entrega, notas
            </code>
          </p>
          <ul className="text-xs text-slate-500 mb-4 space-y-1 list-disc pl-5">
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
            className="block w-full text-sm text-slate-600 file:mr-4 file:rounded-md file:border-0 file:bg-brand-50 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-brand-700 hover:file:bg-brand-100"
          />

          <button
            onClick={handleUpload}
            disabled={!file || loading}
            className="mt-4 bg-brand-600 hover:bg-brand-700 text-white rounded-md px-4 py-2 text-sm font-semibold disabled:opacity-60"
          >
            {loading ? "Procesando..." : "Subir e importar"}
          </button>

          {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
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
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  );
}

/** Resumen de la importación (HU-08): totales y, por fila, qué se omitió o falló y por qué. */
function ImportResult({ summary }: { summary: ImportSummary }) {
  const issues: (ImportRowIssue & { kind: "error" | "skipped" })[] = [
    ...summary.errors.map((e) => ({ ...e, kind: "error" as const })),
    ...summary.skippedRows.map((e) => ({ ...e, kind: "skipped" as const })),
  ].sort((a, b) => a.rowNumber - b.rowNumber);

  return (
    <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
      <h3 className="font-semibold text-slate-800 mb-3">Resultado de la importación</h3>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center mb-4">
        <Stat value={summary.totalRows} label="Filas leídas" className="bg-slate-50 text-slate-800" />
        <Stat value={summary.created} label="Envíos creados" className="bg-green-50 text-green-700" />
        <Stat value={summary.skipped} label="Omitidos (ya existían)" className="bg-amber-50 text-amber-700" />
        <Stat value={summary.failed} label="Con error" className="bg-red-50 text-red-600" />
      </div>

      {issues.length === 0 ? (
        <p className="text-sm text-green-700">Todas las filas se importaron sin problemas.</p>
      ) : (
        <>
          <p className="text-xs text-slate-500 mb-2">
            Corrige las filas con error en la planilla y vuelve a importarla: las filas ya creadas se omiten, no se
            duplican.
          </p>
          <div className="max-h-72 overflow-y-auto border border-slate-200 rounded-md">
            <table className="w-full text-xs">
              <thead className="bg-slate-50 text-slate-500 sticky top-0">
                <tr>
                  <th className="text-left font-semibold px-3 py-2 w-14">Fila</th>
                  <th className="text-left font-semibold px-3 py-2 w-28">Tracking</th>
                  <th className="text-left font-semibold px-3 py-2 w-24">Estado</th>
                  <th className="text-left font-semibold px-3 py-2">Motivo</th>
                </tr>
              </thead>
              <tbody>
                {issues.map((issue) => (
                  <tr key={`${issue.kind}-${issue.rowNumber}`} className="border-t border-slate-100">
                    <td className="px-3 py-2 font-mono">{issue.rowNumber}</td>
                    <td className="px-3 py-2 font-mono text-slate-600">{issue.trackingCode ?? "—"}</td>
                    <td className="px-3 py-2">
                      {issue.kind === "error" ? (
                        <span className="rounded bg-red-50 px-2 py-0.5 font-semibold text-red-600">Error</span>
                      ) : (
                        <span className="rounded bg-amber-50 px-2 py-0.5 font-semibold text-amber-700">Omitido</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-slate-700">{issue.message}</td>
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
