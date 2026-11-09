import "dotenv/config";
import { createApp } from "./app";
import { jwtSecret } from "./config";

// Falla al arrancar, con un mensaje claro, si JWT_SECRET falta o es corto.
try {
  jwtSecret();
} catch (err) {
  console.error(`[import-service] ${(err as Error).message}`);
  process.exit(1);
}

const PORT = process.env.PORT || 8083;

createApp().listen(PORT, () => {
  console.log(`import-service escuchando en el puerto ${PORT}`);
});
