import { NavLink, useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";
import { useRouteDateStore } from "@/store/routeDateStore";
import ThemeToggle from "@/components/ThemeToggle";
import { Logo } from "@/components/icons";
import type { AuthUser } from "@/types";

const ROLE_LABEL: Record<AuthUser["role"], string> = {
  ADMIN: "Administración",
  DISPATCHER: "Despacho",
  OPERATOR: "Operaciones",
  DRIVER: "Conductor",
  CLIENT: "Cliente",
};

interface LayoutProps {
  children: React.ReactNode;
  /** Pantalla completa bajo el header (pantalla de rutas): sin márgenes ni scroll de página. */
  fullBleed?: boolean;
}

/** Header de 56 px compartido por toda la app (HU-27). */
export default function Layout({ children, fullBleed = false }: LayoutProps) {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { date, setDate } = useRouteDateStore();
  const onRoutes = pathname === "/";

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-1.5 text-13 font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
      isActive ? "bg-tile text-ink font-semibold" : "text-muted hover:text-ink hover:bg-tile"
    }`;

  return (
    <div className={fullBleed ? "flex h-[100dvh] flex-col overflow-hidden" : "flex min-h-full flex-col"}>
      <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b border-line bg-panel px-4">
        <div className="flex items-center gap-6">
          <span className="flex items-center gap-2 text-15 font-bold text-ink">
            <Logo />
            LastMile
          </span>
          <nav aria-label="Principal" className="flex items-center gap-1">
            <NavLink to="/" end className={linkClass}>
              Rutas
            </NavLink>
            <NavLink to="/envios" className={linkClass}>
              Envíos
            </NavLink>
            <span
              className="cursor-not-allowed rounded-md px-3 py-1.5 text-13 font-medium text-muted opacity-60"
              title="Disponible más adelante: por ahora, el detalle de cada local se abre desde la ruta"
              aria-disabled="true"
            >
              Locales
            </span>
            <NavLink to="/import" className={linkClass}>
              Importar
            </NavLink>
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {onRoutes && (
            <label className="flex items-center gap-2 text-13 text-muted">
              Fecha
              <input
                type="date"
                value={date}
                onChange={(e) => e.target.value && setDate(e.target.value)}
                className="h-9 rounded-md border border-border bg-panel px-2 text-13 tabular-nums text-ink focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </label>
          )}
          <ThemeToggle />
          {user && (
            <span className="text-13 text-muted">
              {user.fullName.split(" ")[0]} · {ROLE_LABEL[user.role] ?? user.role}
            </span>
          )}
          <button
            type="button"
            onClick={() => {
              logout();
              navigate("/login");
            }}
            className="text-13 text-muted underline-offset-2 hover:text-ink hover:underline"
          >
            Salir
          </button>
        </div>
      </header>
      {fullBleed ? (
        <main className="min-h-0 flex-1">{children}</main>
      ) : (
        <main className="flex-1 p-6">{children}</main>
      )}
    </div>
  );
}
