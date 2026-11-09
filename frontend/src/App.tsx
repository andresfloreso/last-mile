import { Navigate, Route, Routes } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";
import Login from "@/pages/Login";
import RoutePage from "@/pages/RoutePage";
import Import from "@/pages/Import";
import Shipments from "@/pages/Shipments";
import LocalDetail from "@/pages/LocalDetail";

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <RoutePage />
          </RequireAuth>
        }
      />
      <Route
        path="/envios"
        element={
          <RequireAuth>
            <Shipments />
          </RequireAuth>
        }
      />
      <Route
        path="/import"
        element={
          <RequireAuth>
            <Import />
          </RequireAuth>
        }
      />
      <Route
        path="/locales/:customerId"
        element={
          <RequireAuth>
            <LocalDetail />
          </RequireAuth>
        }
      />
    </Routes>
  );
}
