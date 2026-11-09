import { describe, expect, it } from "vitest";
import { toPayload, validateTramos } from "@/lib/declaredWindow";

/** HU-27: validación de los tramos de la modulación antes del PUT declared-window. */
describe("validateTramos", () => {
  it("acepta un tramo válido", () => {
    expect(validateTramos([{ start: "09:00", end: "12:00" }])).toBeNull();
  });

  it("acepta dos tramos que no se solapan, incluso pegados", () => {
    expect(
      validateTramos([
        { start: "09:00", end: "12:00" },
        { start: "16:00", end: "18:00" },
      ])
    ).toBeNull();
    expect(
      validateTramos([
        { start: "09:00", end: "12:00" },
        { start: "12:00", end: "14:00" },
      ])
    ).toBeNull();
  });

  it("sin tramos es válido: el local queda sin modulación", () => {
    expect(validateTramos([])).toBeNull();
  });

  it("exige inicio y término", () => {
    expect(validateTramos([{ start: "09:00", end: "" }])).toBe("El tramo necesita hora de inicio y de término");
    expect(
      validateTramos([
        { start: "09:00", end: "12:00" },
        { start: "", end: "18:00" },
      ])
    ).toBe("El segundo tramo necesita hora de inicio y de término");
  });

  it("rechaza un tramo que termina antes de empezar o dura cero", () => {
    expect(validateTramos([{ start: "12:00", end: "09:00" }])).toBe("El tramo debe empezar antes de terminar");
    expect(validateTramos([{ start: "12:00", end: "12:00" }])).toBe("El tramo debe empezar antes de terminar");
  });

  it("rechaza un segundo tramo que se solapa o va antes del primero", () => {
    const message = "El segundo tramo debe empezar después del término del primero";
    expect(
      validateTramos([
        { start: "09:00", end: "12:00" },
        { start: "11:00", end: "13:00" },
      ])
    ).toBe(message);
    expect(
      validateTramos([
        { start: "15:00", end: "18:00" },
        { start: "09:00", end: "11:00" },
      ])
    ).toBe(message);
  });

  it("rechaza horas con formato inválido y más de dos tramos", () => {
    expect(validateTramos([{ start: "9:00", end: "12:00" }])).toBe("El tramo tiene una hora inválida");
    expect(
      validateTramos([
        { start: "08:00", end: "09:00" },
        { start: "10:00", end: "11:00" },
        { start: "12:00", end: "13:00" },
      ])
    ).toBe("La modulación admite como máximo dos tramos");
  });
});

describe("toPayload", () => {
  it("arma el cuerpo del PUT con uno, dos o ningún tramo", () => {
    expect(toPayload([{ start: "09:00", end: "12:00" }])).toEqual({ start: "09:00", end: "12:00", start2: null, end2: null });
    expect(
      toPayload([
        { start: "09:00", end: "12:00" },
        { start: "16:00", end: "18:00" },
      ])
    ).toEqual({ start: "09:00", end: "12:00", start2: "16:00", end2: "18:00" });
    expect(toPayload([])).toEqual({ start: null, end: null, start2: null, end2: null });
  });
});
