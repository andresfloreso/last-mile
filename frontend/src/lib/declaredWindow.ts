/** Validación y cuerpo del PUT de la modulación (HU-27 sobre la API de HU-25). */

/** Lo que se envía a PUT /api/customers/{id}/declared-window (todo en null = sin modulación). */
export interface DeclaredWindowPayload {
  start: string | null;
  end: string | null;
  start2: string | null;
  end2: string | null;
}

export interface TramoInput {
  start: string;
  end: string;
}

/**
 * Valida los tramos de la modulación antes de enviarlos (el backend valida lo mismo, HU-25).
 * Devuelve el mensaje de error o null si está bien. Sin tramos = el local queda sin modulación.
 */
export function validateTramos(tramos: TramoInput[]): string | null {
  if (tramos.length > 2) return "La modulación admite como máximo dos tramos";
  for (const [i, t] of tramos.entries()) {
    const which = tramos.length === 2 ? (i === 0 ? "El primer tramo" : "El segundo tramo") : "El tramo";
    if (!t.start || !t.end) return `${which} necesita hora de inicio y de término`;
    if (!/^\d{2}:\d{2}$/.test(t.start) || !/^\d{2}:\d{2}$/.test(t.end)) return `${which} tiene una hora inválida`;
    if (t.start >= t.end) return `${which} debe empezar antes de terminar`;
  }
  if (tramos.length === 2 && tramos[1].start < tramos[0].end) {
    return "El segundo tramo debe empezar después del término del primero";
  }
  return null;
}

/** Convierte los tramos del formulario en el cuerpo del PUT. */
export function toPayload(tramos: TramoInput[]): DeclaredWindowPayload {
  return {
    start: tramos[0]?.start ?? null,
    end: tramos[0]?.end ?? null,
    start2: tramos[1]?.start ?? null,
    end2: tramos[1]?.end ?? null,
  };
}
