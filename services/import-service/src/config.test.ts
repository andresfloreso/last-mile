import { describe, expect, it } from "vitest";
import { validateJwtSecret } from "./config";

describe("validateJwtSecret", () => {
  it.each([undefined, "", "   "])("falla si falta el secreto (%j)", (secret) => {
    expect(() => validateJwtSecret(secret)).toThrow(/JWT_SECRET no está definido/);
  });

  it("falla si tiene menos de 32 caracteres", () => {
    expect(() => validateJwtSecret("a".repeat(31))).toThrow(/al menos 32 caracteres \(tiene 31\)/);
  });

  it("acepta un secreto de 32 caracteres o más", () => {
    const secret = "b".repeat(32);
    expect(validateJwtSecret(secret)).toBe(secret);
  });
});
