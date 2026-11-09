import type { AddressInfo } from "node:net";
import type { Server } from "node:http";
import jwt from "jsonwebtoken";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const SECRET = "secreto-de-prueba-de-al-menos-32-caracteres!!";
process.env.JWT_SECRET = SECRET;

let server: Server;
let base: string;

beforeAll(async () => {
  const { createApp } = await import("./app");
  server = createApp().listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});

afterAll(() => {
  server.close();
});

function token(role: string, type = "access") {
  return jwt.sign({ sub: "u-1", role, type }, SECRET, { expiresIn: 60 });
}

function get(path: string, role?: string) {
  return fetch(base + path, { headers: role ? { Authorization: `Bearer ${token(role)}` } : {} });
}

/** #44: import-service deniega por defecto; CLIENT solo llega a lo público. */
describe("denegar por defecto", () => {
  it("health es público", async () => {
    expect((await get("/actuator/health")).status).toBe(200);
  });

  it("sin token responde 401", async () => {
    expect((await get("/api/import/template")).status).toBe(401);
    expect((await get("/api/cualquier-cosa")).status).toBe(401);
  });

  it.each([
    ["GET", "/api/import/template"],
    ["POST", "/api/import/shipments"],
    ["GET", "/api/no-existe"],
  ])("CLIENT recibe 403 en %s %s", async (method, path) => {
    const res = await fetch(base + path, { method, headers: { Authorization: `Bearer ${token("CLIENT")}` } });
    expect(res.status).toBe(403);
  });

  it("un rol interno descarga la plantilla", async () => {
    const res = await get("/api/import/template", "DISPATCHER");
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toContain("spreadsheetml");
  });

  it("DRIVER es interno pero no importa planillas", async () => {
    expect((await get("/api/import/template", "DRIVER")).status).toBe(403);
  });

  it("una ruta inexistente responde 404 a un rol interno", async () => {
    expect((await get("/api/no-existe", "OPERATOR")).status).toBe(404);
  });
});
