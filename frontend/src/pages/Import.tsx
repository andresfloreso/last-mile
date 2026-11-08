import { useState } from "react";
import Layout from "@/components/Layout";
import { importApi } from "@/api/endpoints";
import type { ImportSummary } from "@/types";

export default function Import() {
  const [file, setFile] = useState<File | null>(null);
  const [summary, setSummary] = useState<ImportSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
          <h2 className="font-semibold text-slate-800 mb-1">Importar planilla de envíos</h2>
          <p className="text-sm text-slate-500 mb-4">
            Acepta archivos <code>.xlsx</code>, <code>.xls</code> o <code>.csv</code> con las columnas:
            <code className="block bg-slate-50 rounded-md p-2 mt-2 text-xs">
              cliente, telefono, direccion, comuna, latitud, longitud, codigo_externo, tracking, fecha_entrega, notas
            </code>
          </p>

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

        {summary && (
          <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
            <h3 className="font-semibold text-slate-800 mb-3">Resultado de la importación</h3>
            <div className="grid grid-cols-3 gap-3 text-center mb-4">
              <div className="bg-slate-50 rounded-md p-3">
                <div className="text-2xl font-bold text-slate-800">{summary.totalRows}</div>
                <div className="text-xs text-slate-500">Filas leídas</div>
              </div>
              <div className="bg-green-50 rounded-md p-3">
                <div className="text-2xl font-bold text-green-700">{summary.created}</div>
                <div className="text-xs text-slate-500">Envíos creados</div>
              </div>
              <div className="bg-red-50 rounded-md p-3">
                <div className="text-2xl font-bold text-red-600">{summary.skipped}</div>
                <div className="text-xs text-slate-500">Con error</div>
              </div>
            </div>
            {summary.errors.length > 0 && (
              <ul className="text-xs text-red-600 space-y-1 max-h-48 overflow-y-auto">
                {summary.errors.map((e, i) => (
                  <li key={i}>{e}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </Layout>
  );
}
