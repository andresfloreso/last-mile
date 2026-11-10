import L from "leaflet";
import type { StopView } from "@/lib/routeView";
import { bandVars } from "@/lib/timeWindows";

/**
 * Ícono de una parada (HU-27): círculo con el número y el color de su franja.
 * - Seleccionada: más grande, con borde del color de texto.
 * - Fuera de ventana: anillo del color de rechazo.
 * - Local nuevo: borde punteado.
 * - Por revisar: un "!" pequeño.
 * - Varios envíos en la parada (HU-30): un contador pequeño abajo a la derecha.
 * Los colores son variables CSS, así cambian con el tema sin recrear el ícono.
 */
export function stopIcon(stop: StopView, selected: boolean): L.DivIcon {
  const size = selected ? 36 : 28;
  const v = bandVars(stop.band.id);
  const fill = stop.isNew ? v.bg : v.solid;
  const ink = stop.isNew ? v.text : "var(--color-badge-ink)";
  const border = selected
    ? "3px solid var(--color-text)"
    : stop.isNew
      ? `2px dashed ${v.solid}`
      : "2px solid var(--color-panel)";
  const ring = stop.outsideWindow ? "0 0 0 3px var(--color-reject)" : "0 1px 3px rgba(0,0,0,.25)";
  const review = stop.needsReview
    ? `<span style="position:absolute;top:-5px;right:-5px;width:14px;height:14px;border-radius:50%;background:var(--color-panel);color:var(--color-reject);font:700 11px/14px Inter,sans-serif;text-align:center;border:1px solid var(--color-reject)">!</span>`
    : "";
  const count =
    stop.shipmentCount > 1
      ? `<span style="position:absolute;bottom:-6px;right:-8px;min-width:16px;height:16px;padding:0 3px;box-sizing:border-box;border-radius:8px;background:var(--color-text);color:var(--color-page);font:700 10px/16px Inter,sans-serif;text-align:center;border:1px solid var(--color-panel)">×${stop.shipmentCount}</span>`
      : "";
  return L.divIcon({
    className: "lm-marker",
    html: `<div style="position:relative;box-sizing:border-box;width:${size}px;height:${size}px;border-radius:50%;background:${fill};color:${ink};border:${border};box-shadow:${ring};display:flex;align-items:center;justify-content:center;font:700 ${selected ? 14 : 12}px Inter,sans-serif">${stop.position}${review}${count}</div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}

/** Bodega: marcador cuadrado "B". */
export function depotIcon(): L.DivIcon {
  return L.divIcon({
    className: "lm-marker",
    html: `<div style="box-sizing:border-box;width:26px;height:26px;border-radius:6px;background:var(--color-text);color:var(--color-page);border:2px solid var(--color-panel);display:flex;align-items:center;justify-content:center;font:700 12px Inter,sans-serif">B</div>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
  });
}
