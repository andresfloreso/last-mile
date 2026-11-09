import { create } from "zustand";
import { localToday } from "@/lib/timeWindows";

/** Fecha de la ruta: la elige el selector del header y la usa la pantalla de rutas (HU-27). */
interface RouteDateState {
  date: string;
  setDate: (date: string) => void;
}

export const useRouteDateStore = create<RouteDateState>((set) => ({
  date: localToday(),
  setDate: (date) => set({ date }),
}));
