import { afterEach, describe, expect, it, vi } from "vitest";

async function loadConfig() {
  vi.resetModules();
  return import("./config");
}

describe("mapa base (HU-27, #57)", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("sin variables usa Stadia Alidade Smooth, sin key y con detalle hasta zoom 20", async () => {
    const { TILE_URL, TILE_ATTRIBUTION, TILE_MAX_NATIVE_ZOOM, TILE_PROVIDER_NAME } = await loadConfig();
    expect(TILE_PROVIDER_NAME).toBe("stadia");
    expect(TILE_URL.light).toBe("https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png");
    expect(TILE_URL.dark).toContain("alidade_smooth_dark");
    expect(TILE_ATTRIBUTION).toContain("Stadia Maps");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(20);
  });

  it("variables vacías (compose sin valor en .env) también usan el proveedor por defecto", async () => {
    vi.stubEnv("VITE_TILE_PROVIDER", "");
    vi.stubEnv("VITE_TILE_API_KEY", " ");
    vi.stubEnv("VITE_TILE_URL_LIGHT", "");
    vi.stubEnv("VITE_TILE_URL_DARK", " ");
    vi.stubEnv("VITE_TILE_ATTRIBUTION", "");
    vi.stubEnv("VITE_TILE_MAX_NATIVE_ZOOM", "");
    const { TILE_URL, TILE_MAX_NATIVE_ZOOM } = await loadConfig();
    expect(TILE_URL.light).not.toContain("api_key");
    expect(TILE_URL.light).toContain("alidade_smooth");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(20);
  });

  it("VITE_TILE_PROVIDER=esri-gris vuelve al mapa de HU-27", async () => {
    vi.stubEnv("VITE_TILE_PROVIDER", "esri-gris");
    const { TILE_URL, TILE_ATTRIBUTION, TILE_MAX_NATIVE_ZOOM } = await loadConfig();
    expect(TILE_URL.light).toContain("World_Light_Gray_Base");
    expect(TILE_URL.dark).toContain("World_Dark_Gray_Base");
    expect(TILE_ATTRIBUTION).toContain("Esri");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(16);
  });

  it("agrega la key con el parámetro de cada proveedor", async () => {
    vi.stubEnv("VITE_TILE_PROVIDER", "MapTiler");
    vi.stubEnv("VITE_TILE_API_KEY", "abc 123");
    const { TILE_URL } = await loadConfig();
    expect(TILE_URL.light).toBe("https://api.maptiler.com/maps/dataviz/256/{z}/{x}/{y}{r}.png?key=abc%20123");
    expect(TILE_URL.dark).toContain("dataviz-dark");
  });

  it("un proveedor sin key (osm) no recibe la key aunque esté definida", async () => {
    vi.stubEnv("VITE_TILE_PROVIDER", "osm");
    vi.stubEnv("VITE_TILE_API_KEY", "abc");
    const { TILE_URL, TILE_MAX_NATIVE_ZOOM } = await loadConfig();
    expect(TILE_URL.light).toBe("https://tile.openstreetmap.org/{z}/{x}/{y}.png");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(19);
  });

  it("un proveedor desconocido cae en el de por defecto", async () => {
    vi.stubEnv("VITE_TILE_PROVIDER", "google");
    const { TILE_PROVIDER_NAME } = await loadConfig();
    expect(TILE_PROVIDER_NAME).toBe("stadia");
  });

  it("las URLs explícitas pisan al proveedor", async () => {
    vi.stubEnv("VITE_TILE_PROVIDER", "carto");
    vi.stubEnv("VITE_TILE_URL_LIGHT", "https://tiles.example/light/{z}/{x}/{y}.png?api_key=k");
    vi.stubEnv("VITE_TILE_URL_DARK", "https://tiles.example/dark/{z}/{x}/{y}.png?api_key=k");
    vi.stubEnv("VITE_TILE_ATTRIBUTION", "© Proveedor");
    vi.stubEnv("VITE_TILE_MAX_NATIVE_ZOOM", "19");
    const { TILE_URL, TILE_ATTRIBUTION, TILE_MAX_NATIVE_ZOOM } = await loadConfig();
    expect(TILE_URL.light).toBe("https://tiles.example/light/{z}/{x}/{y}.png?api_key=k");
    expect(TILE_URL.dark).toBe("https://tiles.example/dark/{z}/{x}/{y}.png?api_key=k");
    expect(TILE_ATTRIBUTION).toBe("© Proveedor");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(19);
  });
});

describe("bodega (HU-31)", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("por defecto queda dentro de la zona de reparto de la semilla", async () => {
    const { DEPOT } = await loadConfig();
    // Misma caja que SeedDataIT: Santiago, Providencia y Ñuñoa.
    expect(DEPOT.lat).toBeGreaterThanOrEqual(-33.47);
    expect(DEPOT.lat).toBeLessThanOrEqual(-33.41);
    expect(DEPOT.lng).toBeGreaterThanOrEqual(-70.66);
    expect(DEPOT.lng).toBeLessThanOrEqual(-70.585);
  });
});
