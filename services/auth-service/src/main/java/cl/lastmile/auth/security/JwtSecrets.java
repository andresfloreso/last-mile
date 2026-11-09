package cl.lastmile.auth.security;

/**
 * Valida el JWT_SECRET compartido entre servicios (HS256). No existe un valor
 * por defecto: si falta o es corto, el servicio falla al arrancar con un
 * mensaje claro en vez de aceptar tokens firmados con una clave conocida.
 */
public final class JwtSecrets {

    /** HS256 exige una clave de al menos 256 bits. */
    public static final int MIN_LENGTH = 32;

    private JwtSecrets() {
    }

    public static String require(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidJwtSecretException(
                    "JWT_SECRET no está definido. Defínelo en .env (ver .env.example) con al menos "
                            + MIN_LENGTH + " caracteres; por ejemplo: openssl rand -base64 48");
        }
        if (secret.length() < MIN_LENGTH) {
            throw new InvalidJwtSecretException(
                    "JWT_SECRET debe tener al menos " + MIN_LENGTH + " caracteres (tiene "
                            + secret.length() + "). Genera uno con: openssl rand -base64 48");
        }
        return secret;
    }

    /** JWT_SECRET ausente o inválido; {@link JwtSecretFailureAnalyzer} lo muestra al arrancar. */
    public static class InvalidJwtSecretException extends IllegalStateException {
        public InvalidJwtSecretException(String message) {
            super(message);
        }
    }
}
