import { describe, expect, it } from "vitest";
import {
  countDelta,
  draftLine,
  driveBand,
  kmDelta,
  moveStop,
  routeSegments,
  sameOrder,
  timeDelta,
  windowAlert,
  type LegStop,
} from "./routeEdit";

function leg(id: string, position: number, departure: string, path: [number, number][], dayOffset = 0): LegStop {
  const last = path[path.length - 1];
  return { shipmentId: id, position, latitude: last[0], longitude: last[1], path, departure, dayOffset };
}

describe("driveBand", () => {
  it("usa los cortes 12, 15 y 19 h", () => {
    expect(driveBand("07:00")).toBe("morning");
    expect(driveBand("11:59")).toBe("morning");
    expect(driveBand("12:00")).toBe("midday");
    expect(driveBand("14:59")).toBe("midday");
    expect(driveBand("15:00")).toBe("afternoon");
    expect(driveBand("18:59")).toBe("afternoon");
    expect(driveBand("19:00")).toBe("night");
    expect(driveBand("01:00", 1)).toBe("night");
  });
});

describe("routeSegments", () => {
  it("cada tramo toma la hora de salida de la parada anterior y junta los de la misma franja", () => {
    const stops = [
      leg("b", 2, "12:20", [[1, 0], [2, 0]]),
      leg("a", 1, "11:50", [[0, 0], [1, 0]]),
      leg("c", 3, "15:10", [[2, 0], [3, 0]]),
    ];
    const segments = routeSegments(stops, "08:00", [[3, 0], [0, 0]]);
    expect(segments.map((s) => s.band)).toEqual(["morning", "midday", "afternoon"]);
    // bodega→a (sale 08:00) y a→b (sale de a a las 11:50) van juntos en azul.
    expect(segments[0].positions).toEqual([[0, 0], [1, 0], [2, 0]]);
    expect(segments[1].positions).toEqual([[2, 0], [3, 0]]);
    expect(segments[2].positions).toEqual([[3, 0], [0, 0]]);
  });

  it("sin tramos no hay línea", () => {
    expect(routeSegments([], "08:00")).toEqual([]);
  });
});

describe("moveStop", () => {
  const none = new Set<string>();
  it("mueve hacia abajo y hacia arriba", () => {
    expect(moveStop(["a", "b", "c", "d"], none, 0, 2)).toEqual(["b", "c", "a", "d"]);
    expect(moveStop(["a", "b", "c", "d"], none, 3, 1)).toEqual(["a", "d", "b", "c"]);
  });

  it("las fijadas no se mueven ni se corren", () => {
    const pinned = new Set(["b"]);
    // a baja al lugar de c: b se queda segunda.
    expect(moveStop(["a", "b", "c", "d"], pinned, 0, 2)).toEqual(["c", "b", "a", "d"]);
    // d sube hasta arriba: b sigue segunda.
    expect(moveStop(["a", "b", "c", "d"], pinned, 3, 0)).toEqual(["d", "b", "a", "c"]);
    // una fijada no se arrastra
    expect(moveStop(["a", "b", "c"], pinned, 1, 0)).toEqual(["a", "b", "c"]);
  });

  it("soltar sobre una fijada deja la parada en el lugar libre más cercano", () => {
    const pinned = new Set(["c"]);
    expect(moveStop(["a", "b", "c", "d"], pinned, 0, 2)).toEqual(["b", "a", "c", "d"]);
    expect(moveStop(["a", "b", "c", "d"], pinned, 3, 2)).toEqual(["a", "b", "c", "d"]);
    expect(moveStop(["a", "b", "c", "d", "e"], pinned, 4, 2)).toEqual(["a", "b", "c", "e", "d"]);
  });
});

describe("draftLine", () => {
  it("reusa los tramos conocidos y une con rectas los nuevos", () => {
    const depot: [number, number] = [0, 0];
    const stops = [
      leg("a", 1, "09:00", [[0, 0], [0.5, 0.5], [1, 0]]),
      leg("b", 2, "10:00", [[1, 0], [1.5, 0.5], [2, 0]]),
    ];
    expect(draftLine(["a", "b"], stops, depot)).toEqual([[0, 0], [0.5, 0.5], [1, 0], [1.5, 0.5], [2, 0], [0, 0]]);
    expect(draftLine(["b", "a"], stops, depot)).toEqual([[0, 0], [2, 0], [1, 0], [0, 0]]);
  });
});

describe("windowAlert", () => {
  it("rojo si queda fuera, ámbar a menos de 60 min del cierre", () => {
    expect(windowAlert("10:30", "11:00", true).alert).toBe("outside");
    expect(windowAlert("10:30", "11:00:00", false)).toEqual({ alert: "near", minutesToClose: 30 });
    expect(windowAlert("10:00", "11:00:00", false)).toEqual({ alert: null, minutesToClose: 60 });
    expect(windowAlert("01:00", "11:00", false, 1).alert).toBe("outside");
  });
});

describe("diferencias frente a la ruta del sistema", () => {
  it("km, hora y paradas", () => {
    expect(kmDelta(49_700, 48_500)).toBe("+1,2 km vs. sistema");
    expect(kmDelta(48_100, 48_500)).toBe("−0,4 km vs. sistema");
    expect(kmDelta(48_520, 48_500)).toBe("igual que el sistema");
    expect(timeDelta("19:31", 0, "19:17", 0)).toBe("+14 min vs. sistema");
    expect(timeDelta("18:12", 0, "19:17", 0)).toBe("−1 h 05 min vs. sistema");
    expect(timeDelta("00:10", 1, "23:50", 0)).toBe("+20 min vs. sistema");
    expect(countDelta(3, 2)).toBe("+1 vs. sistema");
    expect(countDelta(2, 2)).toBe("igual que el sistema");
  });

  it("sameOrder", () => {
    expect(sameOrder(["a", "b"], ["a", "b"])).toBe(true);
    expect(sameOrder(["a", "b"], ["b", "a"])).toBe(false);
  });
});
