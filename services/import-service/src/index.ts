import "dotenv/config";
import express from "express";

import helmet from "helmet";
import importRoutes from "./routes/importRoutes";
import { jwtSecret } from "./config";

// Falla al arrancar, con un mensaje claro, si JWT_SECRET falta o es corto.
try {
  jwtSecret();
} catch (err) {
  console.error(`[import-service] ${(err as Error).message}`);
  process.exit(1);
}

const app = express();
const PORT = process.env.PORT || 8083;

app.use(helmet());

app.use(express.json());

app.get("/actuator/health", (_req, res) => {
  res.json({ status: "UP" });
});

app.use("/api/import", importRoutes);

// Manejador de errores centralizado (incluye errores de multer, p.ej. archivo muy grande)
app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err);
  res.status(400).json({ error: err.message || "Error inesperado" });
});

app.listen(PORT, () => {
  console.log(`import-service escuchando en el puerto ${PORT}`);
});
