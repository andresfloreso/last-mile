/**
 * Configuración de la pantalla de rutas (HU-27). Valores por defecto de desarrollo;
 * se cambian con variables VITE_* al construir el frontend.
 */
const env = import.meta.env;

/** Lee una variable VITE_*; vacía cuenta como no definida (compose pasa "" si no está en .env). */
function envOr(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() !== "" ? value : fallback;
}

/** Bodega desde donde sale el camión: Av. Italia 1300, Providencia, dentro de la zona de la semilla (HU-31). */
export const DEPOT = {
  lat: Number(env.VITE_DEPOT_LAT ?? -33.446393),
  lng: Number(env.VITE_DEPOT_LNG ?? -70.624979),
};

/** Hora de salida del camión (HU-14). */
export const DEPARTURE_TIME = (env.VITE_DEPARTURE_TIME as string | undefined) ?? "08:00";

/**
 * Mapa base neutro, en claro y oscuro.
 *
 * El diseño pide CARTO (Positron / Dark Matter), pero desde 2026 los mapas base públicos de
 * CARTO exigen API key: sin ella, cada tesela dice "API KEY REQUIRED". Por defecto se usa el
 * Gray Canvas de Esri, que no pide key. El proveedor definitivo se elige antes de HU-24
 * (ver docs/plan/hallazgos.md). Las variables se leen al construir: en Docker van en .env
 * y llegan como build args. Para usar CARTO con una key:
 *   VITE_TILE_URL_LIGHT=https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png?api_key=...
 *   VITE_TILE_URL_DARK=https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png?api_key=...
 *   VITE_TILE_ATTRIBUTION="© OpenStreetMap contributors © CARTO"
 */
export const TILE_URL = {
  light: envOr(
    env.VITE_TILE_URL_LIGHT,
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}",
  ),
  dark: envOr(
    env.VITE_TILE_URL_DARK,
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
  ),
};

export const TILE_ATTRIBUTION = envOr(
  env.VITE_TILE_ATTRIBUTION,
  'Tiles &copy; Esri &mdash; Esri, HERE, Garmin, &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
);

/** Zoom máximo con teselas propias (Gray Canvas llega a 16); más allá se amplía la tesela. */
export const TILE_MAX_NATIVE_ZOOM = Number(envOr(env.VITE_TILE_MAX_NATIVE_ZOOM, "16"));
