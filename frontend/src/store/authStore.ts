import { create } from "zustand";
import type { AuthUser } from "@/types";

interface Session {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  setSession: (accessToken: string, refreshToken: string, user: AuthUser) => void;
  logout: () => void;
}

const STORAGE_KEY = "lastmile_session";

/**
 * Lee la sesión guardada. Se usa como estado inicial del store para que el
 * primer render ya la conozca: si se leyera en un useEffect, RequireAuth
 * redirigiría a /login antes de tiempo al recargar la página (#29).
 */
function readStoredSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Partial<Session> | null;
    if (
      session &&
      typeof session.accessToken === "string" &&
      typeof session.refreshToken === "string" &&
      session.user
    ) {
      return { accessToken: session.accessToken, refreshToken: session.refreshToken, user: session.user };
    }
  } catch {
    // JSON corrupto o localStorage no disponible: se trata como sin sesión.
  }
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // localStorage no disponible.
  }
  return null;
}

const stored = readStoredSession();

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: stored?.accessToken ?? null,
  refreshToken: stored?.refreshToken ?? null,
  user: stored?.user ?? null,
  isAuthenticated: stored !== null,

  setSession: (accessToken, refreshToken, user) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken, refreshToken, user }));
    set({ accessToken, refreshToken, user, isAuthenticated: true });
  },

  logout: () => {
    localStorage.removeItem(STORAGE_KEY);
    set({ accessToken: null, refreshToken: null, user: null, isAuthenticated: false });
  },
}));
