import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { customersApi } from "@/api/endpoints";
import { windowLine } from "@/lib/timeWindow";
import type { DraftStop, OrderPreference, StopType, TimeWindow } from "@/types";
import { STOP_LABELS } from "@/types";

interface StopEditorProps {
  stop: DraftStop;
  onChange: (patch: Partial<DraftStop>) => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onClose: () => void;
}

export default function StopEditor({ stop, onChange, onDuplicate, onDelete, onClose }: StopEditorProps) {
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [timeWindow, setTimeWindow] = useState<TimeWindow | null>(null);

  // HU-11: modulación vs. ventana real del local, si la parada ya tiene un local guardado.
  useEffect(() => {
    if (!stop.customerId) return;
    let cancelled = false;
    customersApi
      .timeWindow(stop.customerId)
      .then((tw) => !cancelled && setTimeWindow(tw))
      .catch(() => !cancelled && setTimeWindow(null));
    return () => {
      cancelled = true;
    };
  }, [stop.customerId]);

  return (
    <div className="fixed inset-0 z-30 flex items-end sm:items-center sm:justify-center bg-black/50">
      <div className="bg-slate-900 text-slate-100 w-full sm:max-w-md sm:rounded-2xl rounded-t-2xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-5 pt-4 pb-2 sticky top-0 bg-slate-900 border-b border-slate-800">
          <span className="text-xs text-slate-500 tracking-wide">
            {stop.shipmentId ? "AÑADIDA" : "ID-PENDIENTE"}
          </span>
          <button onClick={onClose} className="text-slate-400 hover:text-white text-sm">✕</button>
        </div>

        <div className="px-5 py-4 space-y-5">
          <div>
            <h2 className="text-xl font-bold">{stop.customerName || "Nueva parada"}</h2>
            <p className="text-sm text-slate-400">{stop.address}</p>
            {timeWindow && (
              <div className="mt-3 rounded-lg bg-slate-800 px-3 py-2">
                <p className="text-sm font-medium text-slate-100">{windowLine(timeWindow)}</p>
                <Link
                  to={`/locales/${timeWindow.customerId}`}
                  className="text-xs text-brand-100 hover:underline"
                >
                  Ver detalle del local →
                </Link>
              </div>
            )}
          </div>

          {/* Etiquetas de color, igual a "Default" / "Afternoon Pickup" de la app de referencia */}
          <div className="flex flex-wrap gap-2">
            {STOP_LABELS.map((l) => (
              <button
                key={l.name}
                onClick={() => onChange({ label: l.name })}
                className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm border ${
                  stop.label === l.name ? "border-brand-500 bg-slate-800" : "border-slate-700"
                }`}
              >
                <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: l.color }} />
                {l.name}
              </button>
            ))}
          </div>

          <div>
            <label className="block text-xs text-slate-500 mb-1">Añadir notas</label>
            <textarea
              value={stop.notes}
              onChange={(e) => onChange({ notes: e.target.value })}
              rows={2}
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm outline-none focus:border-brand-500"
              placeholder="Ej: dejar en conserjería"
            />
          </div>

          <hr className="border-slate-800" />

          {/* Tipo: Entrega / Recogida */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium">Tipo</span>
            <div className="flex bg-slate-800 rounded-lg p-1">
              {(["DELIVERY", "PICKUP"] as StopType[]).map((t) => (
                <button
                  key={t}
                  onClick={() => onChange({ stopType: t })}
                  className={`px-4 py-1.5 rounded-md text-sm ${
                    stop.stopType === t ? "bg-brand-600 text-white" : "text-slate-400"
                  }`}
                >
                  {t === "DELIVERY" ? "Entrega" : "Recogida"}
                </button>
              ))}
            </div>
          </div>

          {/* Paquetes */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium">Paquetes</span>
            <div className="flex items-center gap-3 bg-slate-800 rounded-lg px-2 py-1">
              <button
                onClick={() => onChange({ packageCount: Math.max(1, stop.packageCount - 1) })}
                className="w-7 h-7 flex items-center justify-center text-lg text-slate-300"
              >
                −
              </button>
              <span className="w-6 text-center">{stop.packageCount}</span>
              <button
                onClick={() => onChange({ packageCount: stop.packageCount + 1 })}
                className="w-7 h-7 flex items-center justify-center text-lg text-slate-300"
              >
                +
              </button>
            </div>
          </div>

          {/* Orden: Primera / Automático / Última */}
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium">Orden</span>
            <div className="flex bg-slate-800 rounded-lg p-1">
              {([
                { value: "FIRST", label: "Primera" },
                { value: "AUTO", label: "Automático" },
                { value: "LAST", label: "Última" },
              ] as { value: OrderPreference; label: string }[]).map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => onChange({ orderPreference: opt.value })}
                  className={`px-3 py-1.5 rounded-md text-xs ${
                    stop.orderPreference === opt.value ? "bg-brand-600 text-white" : "text-slate-400"
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          <hr className="border-slate-800" />

          <button
            onClick={onDuplicate}
            className="w-full flex items-center justify-between text-sm text-slate-200 py-2"
          >
            <span>Duplicar parada</span>
            <span>›</span>
          </button>

          {!confirmingDelete ? (
            <button
              onClick={() => setConfirmingDelete(true)}
              className="w-full flex items-center justify-between text-sm text-red-400 py-2"
            >
              <span>Eliminar parada</span>
              <span>›</span>
            </button>
          ) : (
            <div className="bg-red-950/40 border border-red-900 rounded-lg p-3 space-y-2">
              <p className="text-sm text-red-300">¿Eliminar esta parada de la ruta?</p>
              <div className="flex gap-2">
                <button
                  onClick={onDelete}
                  className="flex-1 bg-red-600 hover:bg-red-700 text-white rounded-md py-1.5 text-sm"
                >
                  Sí, eliminar
                </button>
                <button
                  onClick={() => setConfirmingDelete(false)}
                  className="flex-1 bg-slate-800 text-slate-300 rounded-md py-1.5 text-sm"
                >
                  Cancelar
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
