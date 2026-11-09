package cl.lastmile.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-04: emisión y validación de JWT en auth-service. */
class JwtUtilTest {

    private static final String SECRET = "clave-de-prueba-de-al-menos-32-caracteres!!";
    private final JwtUtil jwt = new JwtUtil(SECRET, 3_600_000, 604_800_000);

    @Test
    void elAccessTokenLlevaUsuarioRolYTipo() {
        String token = jwt.generateAccessToken("user-1", "despacho@lastmile.cl", "DISPATCHER");

        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwt.extractRole(token)).isEqualTo("DISPATCHER");
        assertThat(jwt.extractTokenType(token)).isEqualTo("access");
        assertThat(jwt.getAccessExpirationMs()).isEqualTo(3_600_000);
    }

    @Test
    void elRefreshTokenNoLlevaRolYEsDeTipoRefresh() {
        String token = jwt.generateRefreshToken("user-1");

        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.extractUserId(token)).isEqualTo("user-1");
        assertThat(jwt.extractTokenType(token)).isEqualTo("refresh");
        assertThat(jwt.extractRole(token)).isNull();
    }

    @Test
    void elAccessTokenVenceSegunLaConfiguracion() {
        String token = jwt.generateAccessToken("user-1", "a@b.cl", "ADMIN");
        Date expiration = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(token).getPayload().getExpiration();

        long remaining = expiration.getTime() - System.currentTimeMillis();
        assertThat(remaining).isBetween(3_590_000L, 3_600_000L);
    }

    @Test
    void unTokenVencidoNoEsValido() {
        JwtUtil expiring = new JwtUtil(SECRET, -1_000, -1_000);
        String token = expiring.generateAccessToken("user-1", "a@b.cl", "ADMIN");

        assertThat(jwt.isTokenValid(token)).isFalse();
        assertThatThrownBy(() -> jwt.extractUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        JwtUtil other = new JwtUtil("otra-clave-distinta-tambien-de-32-caracteres", 60_000, 60_000);

        assertThat(jwt.isTokenValid(other.generateAccessToken("user-1", "a@b.cl", "ADMIN"))).isFalse();
    }

    @Test
    void textoQueNoEsUnJwtNoEsValido() {
        assertThat(jwt.isTokenValid("no-es-un-jwt")).isFalse();
        assertThat(jwt.isTokenValid("")).isFalse();
    }

    @Test
    void noArrancaConUnaClaveCorta() {
        assertThatThrownBy(() -> new JwtUtil("corta", 1, 1)).isInstanceOf(JwtSecrets.InvalidJwtSecretException.class);
    }
}
