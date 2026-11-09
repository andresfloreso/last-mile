import { describe, expect, it } from "vitest";
import {
  axisPosition,
  BANDS,
  bandForHour,
  bandForWindowStart,
  declaredDiffers,
  declaredSpans,
  declaredText,
  hourOf,
  localToday,
  longDate,
  longRange,
  shortRange,
} from "./timeWindows";

describe("franjas", () => {
  it("cubren el día de 07 a 22 sin huecos ni solapes", () => {
    expect(BANDS.map((b) => [b.label, b.start, b.end])).toEqual([
      ["Mañana", 7, 12],
      ["Mediodía", 12, 15],
      ["Tarde", 15, 19],
      ["Noche", 19, 22],
    ]);
    for (let i = 1; i < BANDS.length; i++) expect(BANDS[i].start).toBe(BANDS[i - 1].end);
  });

  it.each([
    [7, "morning"],
    [11.99, "morning"],
    [12, "midday"],
    [14.5, "midday"],
    [15, "afternoon"],
    [18, "afternoon"],
    [19, "night"],
    [21.5, "night"],
  ])("la hora %s cae en %s", (hour, id) => {
    expect(bandForHour(hour).id).toBe(id);
  });

  it("antes de las 07 es Mañana y desde las 22 es Noche", () => {
    expect(bandForHour(5).id).toBe("morning");
    expect(bandForHour(23).id).toBe("night");
  });

  it("la franja de una parada sale del inicio de su ventana real", () => {
    expect(bandForWindowStart("09:00:00").label).toBe("Mañana");
    expect(bandForWindowStart("12:00").label).toBe("Mediodía");
    // Los bares de la semilla reciben 18–20: empiezan en la Tarde.
    expect(bandForWindowStart("18:00:00").label).toBe("Tarde");
    expect(bandForWindowStart("19:00").label).toBe("Noche");
  });
});

describe("formato de horas", () => {
  it("lee horas con o sin segundos", () => {
    expect(hourOf("09:30")).toBe(9.5);
    expect(hourOf("18:00:00")).toBe(18);
  });

  it("rango corto para chips y largo para el detalle", () => {
    expect(shortRange("12:00:00", "15:00:00")).toBe("12–15");
    expect(shortRange("09:30", "12:00")).toBe("09:30–12");
    expect(longRange("09:00:00", "12:00")).toBe("09:00–12:00");
  });

  it("describe la modulación en uno o dos tramos", () => {
    expect(declaredText({ start: "09:00", end: "12:00" })).toBe("09–12");
    expect(declaredText({ start: "12:00", end: "15:00", start2: "17:00", end2: "19:00" })).toBe("12–15 y 17–19");
    expect(declaredSpans({ start: "09:00", end: "12:30", start2: null, end2: null })).toEqual([[9, 12.5]]);
    expect(declaredSpans(null)).toEqual([]);
  });

  it("escribe la fecha larga en español", () => {
    expect(longDate("2026-11-09")).toBe("Lunes 9 de noviembre");
    expect(longDate("2026-12-25")).toBe("Viernes 25 de diciembre");
  });

  it("usa la fecha local, no la de UTC", () => {
    expect(localToday(new Date(2026, 10, 9, 23, 30))).toBe("2026-11-09");
  });
});

describe("modulación vs. ventana real", () => {
  it("coincide si la ventana real empieza dentro de un tramo declarado", () => {
    expect(declaredDiffers({ start: "09:00", end: "12:00" }, "09:00:00")).toBe(false);
    expect(declaredDiffers({ start: "09:00", end: "12:00" }, "11:00")).toBe(false);
    expect(declaredDiffers({ start: "09:00", end: "12:00", start2: "17:00", end2: "19:00" }, "18:00")).toBe(false);
  });

  it("no coincide si empieza fuera de todos los tramos (el borde de término no cuenta)", () => {
    expect(declaredDiffers({ start: "09:00", end: "12:00" }, "12:00:00")).toBe(true);
    expect(declaredDiffers({ start: "09:00", end: "12:00" }, "18:00")).toBe(true);
  });

  it("sin modulación no hay diferencia que mostrar", () => {
    expect(declaredDiffers(null, "18:00")).toBe(false);
  });
});

describe("eje de la línea de tiempo", () => {
  it("ubica las horas entre 07 y 22 y recorta lo que queda fuera", () => {
    expect(axisPosition(7)).toBe(0);
    expect(axisPosition(22)).toBe(1);
    expect(axisPosition(14.5)).toBeCloseTo(0.5);
    expect(axisPosition(6)).toBe(0);
    expect(axisPosition(25)).toBe(1);
  });
});
