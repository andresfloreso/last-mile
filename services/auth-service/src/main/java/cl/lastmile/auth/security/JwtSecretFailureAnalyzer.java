package cl.lastmile.auth.security;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Muestra el problema de JWT_SECRET en el bloque "APPLICATION FAILED TO START"
 * de Spring Boot, en vez de dejarlo al fondo de la traza de excepciones.
 */
public class JwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<JwtSecrets.InvalidJwtSecretException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, JwtSecrets.InvalidJwtSecretException cause) {
        return new FailureAnalysis(cause.getMessage(),
                "Define JWT_SECRET (mínimo " + JwtSecrets.MIN_LENGTH + " caracteres) en el entorno o en .env. "
                        + "Debe ser el mismo en api-gateway, auth-service, delivery-service e import-service.",
                cause);
    }
}
