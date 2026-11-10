import type { AuthUser } from "@/types";

type Role = AuthUser["role"];

/**
 * Quién usa la pantalla de rutas (HU-32), igual que la seguridad de delivery-service:
 * - ADMIN y DISPATCHER ven la ruta guardada y la arman (POST /api/routes/build);
 * - OPERATOR la ve en solo lectura (GET /api/routes);
 * - DRIVER y CLIENT no la ven.
 */
export function canViewRoutes(role: Role | undefined): boolean {
  return role === "ADMIN" || role === "DISPATCHER" || role === "OPERATOR";
}

export function canBuildRoutes(role: Role | undefined): boolean {
  return role === "ADMIN" || role === "DISPATCHER";
}

/** Envíos de la fecha que no están en la ruta guardada (por ejemplo, importados después de armarla). */
export function unroutedCount(shipmentIds: string[], routedIds: Iterable<string>): number {
  const routed = new Set(routedIds);
  return shipmentIds.filter((id) => !routed.has(id)).length;
}
