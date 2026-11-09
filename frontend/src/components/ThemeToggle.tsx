import { useThemeStore } from "@/store/themeStore";

/** Botón "Modo oscuro / Modo claro" (HU-27). El tema se guarda y aplica a toda la app. */
export default function ThemeToggle() {
  const theme = useThemeStore((s) => s.theme);
  const toggle = useThemeStore((s) => s.toggle);
  const dark = theme === "dark";
  return (
    <button
      type="button"
      onClick={toggle}
      aria-pressed={dark}
      className="h-9 rounded-md border border-border px-3 text-13 font-semibold text-ink hover:bg-tile focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      {dark ? "Modo claro" : "Modo oscuro"}
    </button>
  );
}
