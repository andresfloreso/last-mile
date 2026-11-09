import express from "express";
import helmet from "helmet";
import importRoutes from "./routes/importRoutes";
import { INTERNAL_ROLES, jwtAuth, requireRole } from "./middleware/jwtAuth";

/**
 * Arma la app sin abrir el puerto (index.ts la escucha; los tests usan un puerto libre).
 *
 * Denegar por defecto (#44): fuera de /actuator/health, todo exige un JWT válido de
 * un rol interno. CLIENT no entra a nada; cada ruta puede restringir más.
 */
export function createApp(): express.Express {
  const app = express();
  app.use(helmet());
  app.use(express.json());

  app.get("/actuator/health", (_req, res) => {
    res.json({ status: "UP" });
  });

  app.use(jwtAuth, requireRole(...INTERNAL_ROLES));

  app.use("/api/import", importRoutes);

  app.use((_req, res) => {
    res.status(404).json({ error: "Recurso no encontrado" });
  });

  // Manejador de errores centralizado (incluye errores de multer, p.ej. archivo muy grande)
  app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    console.error(err);
    res.status(400).json({ error: err.message || "Error inesperado" });
  });

  return app;
}
