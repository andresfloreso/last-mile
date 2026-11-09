import { afterEach, describe, expect, it, vi } from "vitest";

async function loadConfig() {
  vi.resetModules();
  return import("./config");
}

describe("mapa base (HU-27)", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("sin variables usa Esri Gray Canvas", async () => {
    const { TILE_URL, TILE_ATTRIBUTION, TILE_MAX_NATIVE_ZOOM } = await loadConfig();
    expect(TILE_URL.light).toContain("World_Light_Gray_Base");
    expect(TILE_URL.dark).toContain("World_Dark_Gray_Base");
    expect(TILE_ATTRIBUTION).toContain("Esri");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(16);
  });

  it("variables vacías (compose sin valor en .env) también usan Esri", async () => {
    vi.stubEnv("VITE_TILE_URL_LIGHT", "");
    vi.stubEnv("VITE_TILE_URL_DARK", " ");
    vi.stubEnv("VITE_TILE_ATTRIBUTION", "");
    vi.stubEnv("VITE_TILE_MAX_NATIVE_ZOOM", "");
    const { TILE_URL, TILE_ATTRIBUTION, TILE_MAX_NATIVE_ZOOM } = await loadConfig();
    expect(TILE_URL.light).toContain("World_Light_Gray_Base");
    expect(TILE_URL.dark).toContain("World_Dark_Gray_Base");
    expect(TILE_ATTRIBUTION).toContain("Esri");
    expect(TILE_MAX_NATIVE_ZOOM).toBe(16);
  });

  it("con variables usa el proveedor configurado", async () => {
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
