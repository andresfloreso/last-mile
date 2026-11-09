/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_TILE_URL_LIGHT?: string;
  readonly VITE_TILE_URL_DARK?: string;
  readonly VITE_DEPOT_LAT?: string;
  readonly VITE_DEPOT_LNG?: string;
  readonly VITE_DEPARTURE_TIME?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
