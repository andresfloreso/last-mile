import type { RouteState } from "@/types";

/** Texto y color del estado de la ruta (HU-17). */
export function statusChip(state: RouteState | null | undefined): { text: string; className: string } {
  switch (state?.status) {
    case "CONFIRMED":
      return { text: "Confirmada", className: "bg-band-midday-bg text-band-midday-ink" };
    case "IN_PROGRESS":
      return { text: "En curso", className: "bg-band-morning-bg text-band-morning-ink" };
    case "CLOSED":
      return { text: "Cerrada", className: "bg-tile text-muted" };
    default:
      return { text: "Borrador", className: "bg-warning-bg text-warning-ink" };
  }
}
