import axios from "axios";
import { useAuthStore } from "@/store/authStore";
import { installSessionRefresh } from "@/api/sessionRefresh";
import type { AuthResponse } from "@/types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// HU-06: si el access token expiró (401), se renueva con el refresh token y se reintenta la
// request. Solo si el refresh falla se cierra la sesión y se vuelve al login.
// El refresh usa un cliente aparte, sin interceptores, para no entrar en un ciclo.
const refreshClient = axios.create({ baseURL: API_BASE_URL });

installSessionRefresh(apiClient, {
  getRefreshToken: () => useAuthStore.getState().refreshToken,
  refresh: (refreshToken) =>
    refreshClient.post<AuthResponse>("/api/auth/refresh", { refreshToken }).then((r) => r.data),
  onRefreshed: (s) => useAuthStore.getState().setSession(s.accessToken, s.refreshToken, s.user),
  onFailed: () => {
    useAuthStore.getState().logout();
    if (window.location.pathname !== "/login") window.location.href = "/login";
  },
});
