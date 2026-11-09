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

/** Mapa base neutro de CARTO: Positron en claro, Dark Matter en oscuro. */
export const TILE_URL = {
  light:
    (env.VITE_TILE_URL_LIGHT as string | undefined) ??
    "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
  dark:
    (env.VITE_TILE_URL_DARK as string | undefined) ??
    "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
};

export const TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
