package cl.lastmile.auth.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void fallaSiFaltaElSecreto(String secret) {
        assertThatThrownBy(() -> JwtSecrets.require(secret))
                .isInstanceOf(JwtSecrets.InvalidJwtSecretException.class)
                .hasMessageContaining("JWT_SECRET no está definido");
    }

    @Test
    void fallaSiTieneMenosDe32Caracteres() {
        assertThatThrownBy(() -> JwtSecrets.require("a".repeat(31)))
                .isInstanceOf(JwtSecrets.InvalidJwtSecretException.class)
                .hasMessageContaining("al menos 32 caracteres (tiene 31)");
    }

    @Test
    void aceptaUnSecretoDe32CaracteresOMas() {
        String secret = "b".repeat(32);
        assertThat(JwtSecrets.require(secret)).isEqualTo(secret);
    }

    @Test
    void elFailureAnalyzerMuestraElMotivoAlArrancar() {
        var cause = new JwtSecrets.InvalidJwtSecretException("JWT_SECRET no está definido.");
        var analysis = new JwtSecretFailureAnalyzer().analyze(new RuntimeException("bean", cause));
        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo("JWT_SECRET no está definido.");
        assertThat(analysis.getAction()).contains("JWT_SECRET", "32");
    }
}
