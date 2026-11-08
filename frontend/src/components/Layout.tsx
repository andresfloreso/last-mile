import { NavLink, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";

export default function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-2 rounded-md text-sm font-medium ${
      isActive ? "bg-brand-600 text-white" : "text-slate-600 hover:bg-slate-100"
    }`;

  return (
    <div className="min-h-full flex flex-col">
      <header className="bg-white border-b border-slate-200 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <span className="font-bold text-brand-700 text-lg">LastMile</span>
          <nav className="flex gap-1">
            <NavLink to="/" end className={linkClass}>Rutas</NavLink>
            <NavLink to="/import" className={linkClass}>Importar</NavLink>
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500">{user?.fullName} · {user?.role}</span>
          <button
            onClick={() => {
              logout();
              navigate("/login");
            }}
            className="text-sm text-slate-500 hover:text-red-600"
          >
            Cerrar sesión
          </button>
        </div>
      </header>
      <main className="flex-1 p-6">{children}</main>
    </div>
  );
}
