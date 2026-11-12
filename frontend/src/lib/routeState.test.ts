import { describe, expect, it } from "vitest";
import type { RouteState } from "@/types";
import { statusChip } from "./routeState";

function state(status: RouteState["status"]): RouteState {
  return {
    id: "r1",
    date: "2026-11-12",
    status,
    editable: status === "DRAFT",
    optimizer: "VRPTW",
    optimizerNote: null,
    vehiclePlate: "KXPT-21",
    driverId: status === "DRAFT" ? null : "d1",
    driverName: status === "DRAFT" ? null : "Pedro Conductor",
    confirmedAt: null,
  };
}

describe("statusChip (HU-17)", () => {
  it("nombra cada estado de la ruta", () => {
    expect(statusChip(state("DRAFT")).text).toBe("Borrador");
    expect(statusChip(state("CONFIRMED")).text).toBe("Confirmada");
    expect(statusChip(state("IN_PROGRESS")).text).toBe("En curso");
    expect(statusChip(state("CLOSED")).text).toBe("Cerrada");
  });

  it("sin ruta armada es un borrador", () => {
    expect(statusChip(null).text).toBe("Borrador");
  });
});
