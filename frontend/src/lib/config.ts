/**
 * Configuración de la pantalla de rutas (HU-27). Valores por defecto de desarrollo;
 * se cambian con variables VITE_* al construir el frontend.
 */
const env = import.meta.env;

/** Bodega desde donde sale el camión. */
export const DEPOT = {
  lat: Number(env.VITE_DEPOT_LAT ?? -33.4489),
  lng: Number(env.VITE_DEPOT_LNG ?? -70.6693),
};

/** Hora de salida del camión (HU-14). */
export const DEPARTURE_TIME = (env.VITE_DEPARTURE_TIME as string | undefined) ?? "08:00";

/**
 * Mapa base neutro, en claro y oscuro.
 *
 * El diseño pide CARTO (Positron / Dark Matter), pero desde 2026 los mapas base públicos de
 * CARTO exigen API key: sin ella, cada tesela dice "API KEY REQUIRED". Por defecto se usa el
 * Gray Canvas de Esri, que no pide key. Para usar CARTO con una key:
 *   VITE_TILE_URL_LIGHT=https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png?api_key=...
 *   VITE_TILE_URL_DARK=https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png?api_key=...
 *   VITE_TILE_ATTRIBUTION="© OpenStreetMap contributors © CARTO"
 */
export const TILE_URL = {
  light:
    (env.VITE_TILE_URL_LIGHT as string | undefined) ??
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}",
  dark:
    (env.VITE_TILE_URL_DARK as string | undefined) ??
    "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
};

export const TILE_ATTRIBUTION =
  (env.VITE_TILE_ATTRIBUTION as string | undefined) ??
  'Tiles &copy; Esri &mdash; Esri, HERE, Garmin, &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

/** Zoom máximo con teselas propias (Gray Canvas llega a 16); más allá se amplía la tesela. */
export const TILE_MAX_NATIVE_ZOOM = Number(env.VITE_TILE_MAX_NATIVE_ZOOM ?? 16);
