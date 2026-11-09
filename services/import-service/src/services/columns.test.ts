import { describe, expect, it } from "vitest";
import { parseBusinessType, parseModulation, parseTimeOfDay } from "./columns";

describe("parseBusinessType", () => {
  it.each([
    ["Botillería", "BOTILLERIA"],
    ["minimarket", "MINIMARKET"],
    [" BAR ", "BAR"],
    ["Restaurante", "RESTAURANT"],
    ["restaurant", "RESTAURANT"],
    ["otro", "OTRO"],
  ])("%s -> %s", (raw, expected) => {
    expect(parseBusinessType(raw)).toEqual({ ok: true, value: expected });
  });

  it("vacío no es error", () => {
    expect(parseBusinessType(undefined)).toEqual({ ok: true });
    expect(parseBusinessType("  ")).toEqual({ ok: true });
  });

  it("un tipo desconocido es error con los valores permitidos", () => {
    const result = parseBusinessType("kiosco");
    expect(result.ok).toBe(false);
    expect(!result.ok && result.error).toMatch(/tipo_local inválido: "kiosco".*botillería, minimarket, bar, restaurant u otro/);
  });
});

describe("parseTimeOfDay", () => {
  it.each([
    ["9", "09:00"],
    ["09:00", "09:00"],
    ["9:30", "09:30"],
    ["09.30", "09:30"],
    ["13 h", "13:00"],
    ["18:00:00", "18:00"],
    [0.375, "09:00"],
    [0.5208333333, "12:30"],
    [14, "14:00"],
    [new Date(Date.UTC(1899, 11, 30, 19, 15)), "19:15"],
  ])("%j -> %s", (raw, expected) => {
    expect(parseTimeOfDay(raw)).toBe(expected);
  });

  it("vacío es undefined e inválido es null", () => {
    expect(parseTimeOfDay(undefined)).toBeUndefined();
    expect(parseTimeOfDay("")).toBeUndefined();
    expect(parseTimeOfDay("25:00")).toBeNull();
    expect(parseTimeOfDay("9:75")).toBeNull();
    expect(parseTimeOfDay("mañana")).toBeNull();
    expect(parseTimeOfDay(30)).toBeNull();
  });
});

describe("parseModulation", () => {
  it("ambos extremos válidos", () => {
    expect(parseModulation("9", "12:30")).toEqual({
      ok: true,
      value: { declaredWindowStart: "09:00", declaredWindowEnd: "12:30" },
    });
  });

  it("sin modulación", () => {
    expect(parseModulation(undefined, "")).toEqual({ ok: true, value: {} });
  });

  it("un solo extremo es error", () => {
    expect(parseModulation("09:00", undefined)).toEqual({
      ok: false,
      error: "La modulación necesita modulacion_desde y modulacion_hasta",
    });
  });

  it("inicio posterior al término es error", () => {
    const result = parseModulation("15:00", "12:00");
    expect(result.ok).toBe(false);
    expect(!result.ok && result.error).toMatch(/modulacion_desde \(15:00\) debe ser anterior a modulacion_hasta \(12:00\)/);
  });

  it("una hora inválida indica la columna", () => {
    const result = parseModulation("9 de la mañana", "12:00");
    expect(!result.ok && result.error).toMatch(/^modulacion_desde no es una hora válida/);
  });
});
