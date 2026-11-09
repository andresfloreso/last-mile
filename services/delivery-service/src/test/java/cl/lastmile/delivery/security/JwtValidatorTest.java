package cl.lastmile.delivery.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-04: validación de los JWT que emite auth-service (delivery-service solo valida). */
class JwtValidatorTest {

    private static final String SECRET = "clave-de-prueba-de-al-menos-32-caracteres!!";
    private final JwtValidator validator = new JwtValidator(SECRET);

    private static String token(String secret, long expiresInMs, Map<String, Object> claims) {
        return Jwts.builder()
                .subject("user-123")
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void unTokenVigenteFirmadoConLaClaveEsValidoYExponeUsuarioYRol() {
        String token = token(SECRET, 60_000, Map.of("role", "DISPATCHER", "type", "access"));

        assertThat(validator.isValid(token)).isTrue();
        assertThat(validator.extractUserId(token)).isEqualTo("user-123");
        assertThat(validator.extractRole(token)).isEqualTo("DISPATCHER");
    }

    @Test
    void unTokenVencidoNoEsValido() {
        assertThat(validator.isValid(token(SECRET, -1_000, Map.of("role", "ADMIN")))).isFalse();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        String forged = token("otra-clave-distinta-tambien-de-32-caracteres", 60_000, Map.of("role", "ADMIN"));

        assertThat(validator.isValid(forged)).isFalse();
    }

    @Test
    void unTokenAlteradoNoEsValido() {
        String token = token(SECRET, 60_000, Map.of("role", "DRIVER"));
        String[] parts = token.split("\\.");
        // Cambia el rol en el payload sin volver a firmar.
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("DRIVER", "ADMIN");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(validator.isValid(tampered)).isFalse();
    }

    @Test
    void unTokenSinFirmaNoEsValido() {
        String unsigned = Jwts.builder().subject("user-123").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).compact();

        assertThat(validator.isValid(unsigned)).isFalse();
    }

    @Test
    void textoQueNoEsUnJwtNoEsValido() {
        assertThat(validator.isValid("no-es-un-jwt")).isFalse();
        assertThat(validator.isValid("")).isFalse();
    }

    @Test
    void noArrancaConUnaClaveCorta() {
        assertThatThrownBy(() -> new JwtValidator("corta"))
                .isInstanceOf(JwtSecrets.InvalidJwtSecretException.class);
    }
}
