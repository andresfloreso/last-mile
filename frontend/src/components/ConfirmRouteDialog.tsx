import { useEffect, useRef, useState } from "react";
import { usersApi } from "@/api/endpoints";
import type { Driver } from "@/types";

interface Props {
  /** Conductor ya asignado (al cambiarlo en una ruta confirmada). */
  currentDriverId: string | null;
  onConfirm: (driver: Driver) => Promise<void>;
  onClose: () => void;
}

/**
 * Confirmar la ruta y asignarla a un conductor (HU-17). Lista los conductores activos
 * (GET /api/users/drivers). Una ruta confirmada ya no se rearma ni se reordena.
 */
export default function ConfirmRouteDialog({ currentDriverId, onConfirm, onClose }: Props) {
  const [drivers, setDrivers] = useState<Driver[] | null>(null);
  const [driverId, setDriverId] = useState<string>(currentDriverId ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const select = useRef<HTMLSelectElement>(null);

  useEffect(() => {
    usersApi
      .drivers()
      .then((list) => {
        setDrivers(list);
        setDriverId((d) => d || list[0]?.id || "");
      })
      .catch(() => setError("No se pudo cargar la lista de conductores."));
  }, []);

  useEffect(() => {
    if (drivers) select.current?.focus();
  }, [drivers]);

  async function confirm() {
    const driver = drivers?.find((d) => d.id === driverId);
    if (!driver) return;
    setSaving(true);
    setError(null);
    try {
      await onConfirm(driver);
    } catch (e) {
      const err = e as { response?: { data?: { message?: string } } };
      setError(err?.response?.data?.message ?? "No se pudo confirmar la ruta.");
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="confirmar-titulo">
      <button type="button" aria-label="Cerrar" className="absolute inset-0 h-full w-full cursor-default bg-ink opacity-40" onClick={onClose} />
      <div
        className="relative w-full max-w-sm space-y-4 rounded-xl border border-line bg-panel p-5 shadow-lg"
        onKeyDown={(e) => e.key === "Escape" && onClose()}
      >
        <div>
          <h2 id="confirmar-titulo" className="text-lg font-semibold text-ink">
            Confirmar y asignar
          </h2>
          <p className="mt-1 text-13 text-muted">
            El conductor verá la ruta en su teléfono. Una ruta confirmada ya no se reordena; si necesitas cambiarla,
            vuélvela a borrador.
          </p>
        </div>

        {drivers === null && !error && <p className="text-13 text-muted">Cargando conductores…</p>}
        {drivers && drivers.length === 0 && (
          <p className="rounded-lg bg-warning-bg px-3 py-2 text-13 text-warning-ink">
            No hay conductores activos. Administración debe crear un usuario con rol conductor.
          </p>
        )}
        {drivers && drivers.length > 0 && (
          <label className="block space-y-1">
            <span className="text-13 font-semibold text-ink">Conductor</span>
            <select
              ref={select}
              value={driverId}
              onChange={(e) => setDriverId(e.target.value)}
              className="h-10 w-full rounded-lg border border-border bg-panel px-3 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-primary"
            >
              {drivers.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.fullName} · {d.email}
                </option>
              ))}
            </select>
          </label>
        )}
        {error && (
          <p className="rounded-lg bg-warning-bg px-3 py-2 text-13 text-warning-ink" role="alert">
            {error}
          </p>
        )}

        <div className="flex gap-2">
          <button type="button" onClick={onClose} className="h-10 flex-1 rounded-lg border border-border text-sm font-semibold text-ink hover:bg-tile">
            Cancelar
          </button>
          <button
            type="button"
            onClick={confirm}
            disabled={!driverId || saving}
            className="h-10 flex-1 rounded-lg bg-primary text-sm font-semibold text-primary-ink disabled:opacity-50"
          >
            {saving ? "Confirmando…" : "Confirmar ruta"}
          </button>
        </div>
      </div>
    </div>
  );
}
