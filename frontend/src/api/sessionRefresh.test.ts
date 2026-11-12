import { describe, expect, it, vi } from "vitest";
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { installSessionRefresh, type RefreshDeps } from "./sessionRefresh";
import type { AuthResponse } from "@/types";

const session = (n: number): AuthResponse => ({
  accessToken: `access-${n}`,
  refreshToken: `refresh-${n}`,
  tokenType: "Bearer",
  expiresInMs: 3600000,
  user: { id: "u", fullName: "Despacho", email: "d@x.cl", role: "DISPATCHER" },
});

function fail(config: InternalAxiosRequestConfig, status: number): never {
  throw new AxiosError(String(status), "ERR_BAD_REQUEST", config, null, {
    data: {},
    status,
    statusText: "",
    headers: {},
    config,
  });
}

/** Servidor falso: responde 200 solo si el token es uno de los válidos. */
function setup(valid: string[], overrides: Partial<RefreshDeps> = {}) {
  const seen: string[] = [];
  const adapter: AxiosAdapter = async (config) => {
    const auth = String(config.headers.Authorization ?? "");
    seen.push(auth);
    if (!valid.some((t) => auth === `Bearer ${t}`)) fail(config, 401);
    return { data: { url: config.url }, status: 200, statusText: "", headers: {}, config };
  };
  const client = axios.create({ adapter });
  let token = "access-0";
  client.interceptors.request.use((c) => {
    if (!c.headers.Authorization) c.headers.Authorization = `Bearer ${token}`;
    return c;
  });
  const deps = {
    getRefreshToken: () => "refresh-0" as string | null,
    refresh: vi.fn(async () => session(1)),
    onRefreshed: vi.fn((s: AuthResponse) => {
      token = s.accessToken;
    }),
    onFailed: vi.fn(),
    ...overrides,
  };
  installSessionRefresh(client, deps);
  return { client, deps, seen };
}

describe("installSessionRefresh (HU-06)", () => {
  it("ante un 401 renueva el token y reintenta la request", async () => {
    const { client, deps, seen } = setup(["access-1"]);
    const res = await client.get("/api/routes");
    expect(res.status).toBe(200);
    expect(deps.refresh).toHaveBeenCalledWith("refresh-0");
    expect(deps.onRefreshed).toHaveBeenCalledWith(session(1));
    expect(deps.onFailed).not.toHaveBeenCalled();
    expect(seen).toEqual(["Bearer access-0", "Bearer access-1"]);
  });

  it("si el refresh falla, cierra sesión y propaga el 401", async () => {
    const { client, deps } = setup(["access-1"], { refresh: vi.fn(async () => Promise.reject(new Error("401"))) });
    await expect(client.get("/api/routes")).rejects.toMatchObject({ response: { status: 401 } });
    expect(deps.onFailed).toHaveBeenCalledTimes(1);
    expect(deps.onRefreshed).not.toHaveBeenCalled();
  });

  it("sin refresh token no intenta renovar y cierra sesión", async () => {
    const { client, deps } = setup(["access-1"], { getRefreshToken: () => null });
    await expect(client.get("/api/routes")).rejects.toBeTruthy();
    expect(deps.refresh).not.toHaveBeenCalled();
    expect(deps.onFailed).toHaveBeenCalledTimes(1);
  });

  it("si la request reintentada vuelve con 401, cierra sesión sin entrar en un ciclo", async () => {
    const { client, deps, seen } = setup([]);
    await expect(client.get("/api/routes")).rejects.toBeTruthy();
    expect(deps.refresh).toHaveBeenCalledTimes(1);
    expect(deps.onFailed).toHaveBeenCalledTimes(1);
    expect(seen).toHaveLength(2);
  });

  it("varias requests con 401 a la vez comparten un solo refresh", async () => {
    const { client, deps } = setup(["access-1"]);
    const all = await Promise.all([client.get("/a"), client.get("/b"), client.get("/c")]);
    expect(all.map((r) => r.status)).toEqual([200, 200, 200]);
    expect(deps.refresh).toHaveBeenCalledTimes(1);
  });

  it("un 401 del login (clave mala) no intenta renovar ni cierra sesión", async () => {
    const { client, deps } = setup([]);
    await expect(client.post("/api/auth/login", {})).rejects.toBeTruthy();
    expect(deps.refresh).not.toHaveBeenCalled();
    expect(deps.onFailed).not.toHaveBeenCalled();
  });

  it("otros errores (403) pasan sin tocar la sesión", async () => {
    const client = axios.create({ adapter: async (config) => fail(config, 403) });
    const deps = { getRefreshToken: () => "r", refresh: vi.fn(), onRefreshed: vi.fn(), onFailed: vi.fn() };
    installSessionRefresh(client, deps);
    await expect(client.get("/x")).rejects.toMatchObject({ response: { status: 403 } });
    expect(deps.refresh).not.toHaveBeenCalled();
    expect(deps.onFailed).not.toHaveBeenCalled();
  });
});
