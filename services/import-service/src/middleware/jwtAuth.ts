import { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";
import { jwtSecret } from "../config";

export interface AuthenticatedRequest extends Request {
  userId?: string;
  userRole?: string;
}

interface AccessTokenPayload {
  sub: string;
  email?: string;
  role: string;
  type: string;
  exp: number;
}

/**
 * Middleware de autenticación JWT (equivalente Node/Express del JwtFilter de
 * los microservicios Java): exige "Authorization: Bearer <token>", valida
 * firma + expiración + que sea un access token, e inyecta userId/userRole
 * en el request para las capas siguientes.
 */
export function jwtAuth(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  const header = req.headers.authorization;

  if (!header || !header.startsWith("Bearer ")) {
    res.status(401).json({ error: "Falta el header Authorization: Bearer <token>" });
    return;
  }

  const token = header.substring(7);

  try {
    const payload = jwt.verify(token, jwtSecret()) as AccessTokenPayload;

    if (payload.type !== "access") {
      res.status(401).json({ error: "Tipo de token inválido" });
      return;
    }

    req.userId = payload.sub;
    req.userRole = payload.role;
    next();
  } catch (err) {
    res.status(401).json({ error: "Token inválido o expirado" });
  }
}

/** Roles de la distribuidora. CLIENT queda fuera de todo lo no público (#44). */
export const INTERNAL_ROLES = ["ADMIN", "DISPATCHER", "OPERATOR", "DRIVER"] as const;

/** Restringe el acceso a un conjunto de roles (ADMIN, DISPATCHER, OPERATOR, etc). */
export function requireRole(...roles: string[]) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
    if (!req.userRole || !roles.includes(req.userRole)) {
      res.status(403).json({ error: "No tienes permisos para esta operación" });
      return;
    }
    next();
  };
}
