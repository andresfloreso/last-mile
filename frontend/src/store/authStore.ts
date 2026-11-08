import { create } from "zustand";
import type { AuthUser } from "@/types";

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  setSession: (accessToken: string, refreshToken: string, user: AuthUser) => void;
  logout: () => void;
  hydrateFromStorage: () => void;
}

const STORAGE_KEY = "lastmile_session";

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  user: null,
  isAuthenticated: false,

  setSession: (accessToken, refreshToken, user) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken, refreshToken, user }));
    set({ accessToken, refreshToken, user, isAuthenticated: true });
  },

  logout: () => {
    localStorage.removeItem(STORAGE_KEY);
    set({ accessToken: null, refreshToken: null, user: null, isAuthenticated: false });
  },

  hydrateFromStorage: () => {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    try {
      const { accessToken, refreshToken, user } = JSON.parse(raw);
      set({ accessToken, refreshToken, user, isAuthenticated: true });
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    }
  },
}));
