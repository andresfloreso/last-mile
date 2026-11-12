import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";
import type { AuthResponse } from "@/types";

/** Lo que el interceptor necesita de la sesión; se inyecta para poder probarlo sin store ni navegador. */
export interface RefreshDeps {
  /** Refresh token guardado, o null si no hay sesión. */
  getRefreshToken: () => string | null;
  /** Pide tokens nuevos a POST /api/auth/refresh. Debe usar un cliente sin este interceptor. */
  refresh: (refreshToken: string) => Promise<AuthResponse>;
  /** Guarda la sesión renovada. */
  onRefreshed: (session: AuthResponse) => void;
  /** El refresh falló: cerrar sesión y volver a /login. */
  onFailed: () => void;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

/** Login y refresh responden 401 por credenciales malas: ahí no se intenta renovar. */
function isAuthCall(url: string | undefined): boolean {
  return !!url && /\/api\/auth\/(login|refresh)\b/.test(url);
}

/**
 * HU-06: ante un 401, pide tokens nuevos con el refresh token y reintenta la request una vez.
 * Si varias requests reciben 401 a la vez, comparten un solo refresh. Si no hay refresh token,
 * el refresh falla o la request reintentada vuelve con 401, se cierra la sesión.
 */
export function installSessionRefresh(client: AxiosInstance, deps: RefreshDeps): void {
  let pending: Promise<string> | null = null;

  function renew(): Promise<string> {
    if (!pending) {
      const token = deps.getRefreshToken();
      pending = (token ? deps.refresh(token) : Promise.reject(new Error("Sin refresh token")))
        .then((session) => {
          deps.onRefreshed(session);
          return session.accessToken;
        })
        .finally(() => {
          pending = null;
        });
    }
    return pending;
  }

  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const config = error.config as RetriableConfig | undefined;
      if (error.response?.status !== 401 || !config || isAuthCall(config.url)) {
        return Promise.reject(error);
      }
      if (config._retried) {
        deps.onFailed();
        return Promise.reject(error);
      }
      let accessToken: string;
      try {
        accessToken = await renew();
      } catch {
        deps.onFailed();
        return Promise.reject(error);
      }
      config._retried = true;
      config.headers.Authorization = `Bearer ${accessToken}`;
      return client.request(config);
    }
  );
}
