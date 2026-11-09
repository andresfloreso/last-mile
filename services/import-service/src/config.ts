/**
 * Configuración sensible de import-service. No hay valores por defecto: si
 * JWT_SECRET falta o es corto, el servicio no arranca (ver index.ts), en vez
 * de aceptar tokens firmados con una clave conocida.
 */
export const JWT_SECRET_MIN_LENGTH = 32;

export function validateJwtSecret(secret: string | undefined): string {
  if (!secret || secret.trim() === "") {
    throw new Error(
      `JWT_SECRET no está definido. Defínelo en .env (ver .env.example) con al menos ${JWT_SECRET_MIN_LENGTH} caracteres; por ejemplo: openssl rand -base64 48`
    );
  }
  if (secret.length < JWT_SECRET_MIN_LENGTH) {
    throw new Error(
      `JWT_SECRET debe tener al menos ${JWT_SECRET_MIN_LENGTH} caracteres (tiene ${secret.length}). Genera uno con: openssl rand -base64 48`
    );
  }
  return secret;
}

let cachedSecret: string | undefined;

/** Secreto validado de process.env (se valida una vez y queda en caché). */
export function jwtSecret(): string {
  cachedSecret ??= validateJwtSecret(process.env.JWT_SECRET);
  return cachedSecret;
}
