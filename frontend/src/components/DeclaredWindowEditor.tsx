import { useState } from "react";
import type { DeclaredWindow } from "@/types";
import { toPayload, validateTramos, type DeclaredWindowPayload, type TramoInput } from "@/lib/declaredWindow";

function initialTramos(w: DeclaredWindow | null): TramoInput[] {
  if (!w) return [{ start: "09:00", end: "12:00" }];
  const tramos = [{ start: w.start.substring(0, 5), end: w.end.substring(0, 5) }];
  if (w.start2 && w.end2) tramos.push({ start: w.start2.substring(0, 5), end: w.end2.substring(0, 5) });
  return tramos;
}

interface Props {
  value: DeclaredWindow | null;
  onSave: (payload: DeclaredWindowPayload) => Promise<void>;
  onCancel: () => void;
}

/** Formulario pequeño para editar la modulación declarada, en uno o dos tramos (HU-27 sobre la API de HU-25). */
export default function DeclaredWindowEditor({ value, onSave, onCancel }: Props) {
  const [tramos, setTramos] = useState<TramoInput[]>(() => initialTramos(value));
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const update = (i: number, field: keyof TramoInput, v: string) =>
    setTramos((prev) => prev.map((t, j) => (j === i ? { ...t, [field]: v } : t)));

  async function save(next: TramoInput[]) {
    const problem = validateTramos(next);
    setError(problem);
    if (problem) return;
    setSaving(true);
    try {
      await onSave(toPayload(next));
    } catch (e: unknown) {
      const message = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(message ?? "No se pudo guardar la modulación");
    } finally {
      setSaving(false);
    }
  }

  const input =
    "w-[88px] rounded-md border border-border bg-panel px-2 py-1 text-13 tabular-nums text-ink focus:outline-none focus:ring-2 focus:ring-primary";

  return (
    <form
      className="space-y-2 rounded-lg border border-line bg-tile p-3"
      onSubmit={(e) => {
        e.preventDefault();
        save(tramos);
      }}
      aria-label="Editar modulación"
    >
      {tramos.map((t, i) => (
        <fieldset key={i} className="flex items-center gap-2">
          <legend className="sr-only">{i === 0 ? "Primer tramo" : "Segundo tramo"}</legend>
          <label className="sr-only" htmlFor={`mod-start-${i}`}>
            Desde
          </label>
          <input id={`mod-start-${i}`} type="time" value={t.start} onChange={(e) => update(i, "start", e.target.value)} className={input} />
          <span className="text-muted" aria-hidden="true">
            –
          </span>
          <label className="sr-only" htmlFor={`mod-end-${i}`}>
            Hasta
          </label>
          <input id={`mod-end-${i}`} type="time" value={t.end} onChange={(e) => update(i, "end", e.target.value)} className={input} />
          {i === 1 && (
            <button
              type="button"
              onClick={() => setTramos((prev) => prev.slice(0, 1))}
              className="text-xs text-muted underline hover:text-ink"
            >
              Quitar
            </button>
          )}
        </fieldset>
      ))}

      {tramos.length === 1 && (
        <button
          type="button"
          onClick={() => setTramos((prev) => [...prev, { start: "16:00", end: "18:00" }])}
          className="text-xs font-medium text-ink underline"
        >
          Agregar segundo tramo
        </button>
      )}

      {error && (
        <p role="alert" className="text-xs font-medium text-reject">
          {error}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-2 pt-1">
        <button
          type="submit"
          disabled={saving}
          className="h-8 rounded-md bg-primary px-3 text-13 font-semibold text-primary-ink disabled:opacity-60"
        >
          {saving ? "Guardando…" : "Guardar"}
        </button>
        <button type="button" onClick={onCancel} className="h-8 rounded-md border border-border px-3 text-13 text-ink">
          Cancelar
        </button>
        {value && (
          <button type="button" onClick={() => save([])} className="ml-auto text-xs text-muted underline hover:text-ink">
            Sin modulación
          </button>
        )}
      </div>
    </form>
  );
}
