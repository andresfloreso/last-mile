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
 * Mapa base neutro, en claro y oscuro (HU-27, #57).
 *
 * `VITE_TILE_PROVIDER` elige un proveedor ya configurado; `VITE_TILE_API_KEY` es su key, si la pide.
 * Las variables se leen al construir: en Docker van en .env y llegan como build args. La key queda
 * visible en el navegador (va en la URL de cada tesela), así que se restringe por dominio en el panel
 * del proveedor y nunca se versiona. Comparación de proveedores: docs/design/mapa-base.md.
 *
 * | Proveedor   | Sin key                      | Detalle hasta | Plan gratis                        |
 * |-------------|------------------------------|---------------|------------------------------------|
 * | stadia      | solo en localhost            | zoom 20       | 200.000 créditos/mes, no comercial |
 * | esri-gris   | sí                           | zoom 16       | el de HU-27; borroso desde el 17   |
 * | esri-calles | sí                           | zoom 19       | mapa con color, no neutro          |
 * | osm         | sí, solo para desarrollo     | zoom 19       | política de uso de la OSMF         |
 * | maptiler    | no                           | zoom 20       | no comercial, cuota mensual        |
 * | carto       | no (desde 2026)              | zoom 20       | con key de CARTO                   |
 *
 * Por defecto, Stadia (Alidade Smooth): funciona sin key en desarrollo (localhost) y es neutro. En otro
 * dominio hace falta registrar el dominio o una key de Stadia. `VITE_TILE_URL_LIGHT` / `_DARK`,
 * `VITE_TILE_ATTRIBUTION` y `VITE_TILE_MAX_NATIVE_ZOOM` siguen pisando lo que diga el proveedor.
 */
export interface TileProvider {
  light: string;
  dark: string;
  attribution: string;
  /** Zoom máximo con teselas propias; más allá se amplía la tesela. */
  maxNativeZoom: number;
  /** Parámetro de la URL donde va la key (`api_key=`, `key=`), si el proveedor la usa. */
  keyParam?: string;
}

const OSM = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

export const TILE_PROVIDERS: Record<string, TileProvider> = {
  stadia: {
    light: "https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png",
    dark: "https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/{z}/{x}/{y}{r}.png",
    attribution: `&copy; <a href="https://stadiamaps.com/">Stadia Maps</a> &copy; <a href="https://openmaptiles.org/">OpenMapTiles</a> ${OSM}`,
    maxNativeZoom: 20,
    keyParam: "api_key",
  },
  "esri-gris": {
    light: "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}",
    dark: "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
    attribution: `Tiles &copy; Esri &mdash; Esri, HERE, Garmin, ${OSM}`,
    maxNativeZoom: 16,
  },
  "esri-calles": {
    light: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
    // Esri no tiene un mapa de calles oscuro sin key: en modo oscuro se usa el gris oscuro.
    dark: "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
    attribution: `Tiles &copy; Esri &mdash; Esri, HERE, Garmin, ${OSM}`,
    maxNativeZoom: 19,
  },
  osm: {
    // Solo para desarrollo: la política de uso de tile.openstreetmap.org no permite un uso intensivo.
    light: "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    dark: "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    attribution: OSM,
    maxNativeZoom: 19,
  },
  maptiler: {
    light: "https://api.maptiler.com/maps/dataviz/256/{z}/{x}/{y}{r}.png",
    dark: "https://api.maptiler.com/maps/dataviz-dark/256/{z}/{x}/{y}{r}.png",
    attribution: `&copy; <a href="https://www.maptiler.com/copyright/">MapTiler</a> ${OSM}`,
    maxNativeZoom: 20,
    keyParam: "key",
  },
  carto: {
    light: "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
    dark: "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
    attribution: `${OSM} &copy; <a href="https://carto.com/attributions">CARTO</a>`,
    maxNativeZoom: 20,
    keyParam: "api_key",
  },
};

export const DEFAULT_TILE_PROVIDER = "stadia";

/** Nombre del proveedor elegido; uno desconocido cae en el de por defecto. */
export const TILE_PROVIDER_NAME = (() => {
  const name = envOr(env.VITE_TILE_PROVIDER, DEFAULT_TILE_PROVIDER).trim().toLowerCase();
  return name in TILE_PROVIDERS ? name : DEFAULT_TILE_PROVIDER;
})();

const provider = TILE_PROVIDERS[TILE_PROVIDER_NAME];
const apiKey = envOr(env.VITE_TILE_API_KEY, "").trim();

/** Agrega la key a la URL de la tesela, si el proveedor la usa y hay una. */
export function withKey(url: string, p: TileProvider, key: string): string {
  if (!key || !p.keyParam) return url;
  return `${url}${url.includes("?") ? "&" : "?"}${p.keyParam}=${encodeURIComponent(key)}`;
}

export const TILE_URL = {
  light: envOr(env.VITE_TILE_URL_LIGHT, withKey(provider.light, provider, apiKey)),
  dark: envOr(env.VITE_TILE_URL_DARK, withKey(provider.dark, provider, apiKey)),
};

export const TILE_ATTRIBUTION = envOr(env.VITE_TILE_ATTRIBUTION, provider.attribution);

/** Zoom máximo con teselas propias del proveedor; más allá se amplía la tesela. */
export const TILE_MAX_NATIVE_ZOOM = Number(envOr(env.VITE_TILE_MAX_NATIVE_ZOOM, String(provider.maxNativeZoom)));
