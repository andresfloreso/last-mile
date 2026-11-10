import { describe, expect, it } from "vitest";
import { canBuildRoutes, canViewRoutes, unroutedCount } from "./routeAccess";

describe("pantalla de rutas por rol (HU-32)", () => {
  it("despacho y administración ven y arman la ruta", () => {
    for (const role of ["ADMIN", "DISPATCHER"] as const) {
      expect(canViewRoutes(role)).toBe(true);
      expect(canBuildRoutes(role)).toBe(true);
    }
  });

  it("el operador la ve en solo lectura", () => {
    expect(canViewRoutes("OPERATOR")).toBe(true);
    expect(canBuildRoutes("OPERATOR")).toBe(false);
  });

  it("conductor, cliente y sin sesión no la ven", () => {
    for (const role of ["DRIVER", "CLIENT", undefined] as const) {
      expect(canViewRoutes(role)).toBe(false);
      expect(canBuildRoutes(role)).toBe(false);
    }
  });
});

describe("envíos sin ruta", () => {
  it("cuenta los envíos de la fecha que no están en ninguna parada", () => {
    expect(unroutedCount(["a", "b", "c", "d"], ["a", "c"])).toBe(2);
    expect(unroutedCount(["a"], ["a", "x"])).toBe(0);
    expect(unroutedCount([], [])).toBe(0);
  });
});
