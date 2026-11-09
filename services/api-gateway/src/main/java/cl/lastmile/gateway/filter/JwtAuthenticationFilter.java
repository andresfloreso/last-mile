package cl.lastmile.gateway.filter;

import cl.lastmile.gateway.security.JwtSecrets;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Valida el JWT una sola vez en el borde del sistema (API Gateway) para
 * cortar tráfico no autenticado antes de que llegue a los microservicios,
 * y propaga el userId/rol como headers internos (X-User-Id, X-User-Role)
 * para que los servicios downstream no tengan que re-parsear el token.
 *
 * Esos headers solo los escribe el gateway: los que manda el cliente se
 * eliminan siempre, también en las rutas públicas (#9). Así nadie puede
 * suplantar a otro usuario o rol enviándolos a mano.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/tracking",
            "/actuator/health"
    );

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(JwtSecrets.require(secret).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange incoming, GatewayFilterChain chain) {
        // HttpHeaders no distingue mayúsculas: también elimina x-user-id, X-USER-ROLE, etc.
        ServerWebExchange exchange = incoming.mutate()
                .request(incoming.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove(USER_ID_HEADER);
                            headers.remove(USER_ROLE_HEADER);
                        })
                        .build())
                .build();
        String path = exchange.getRequest().getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Falta el header Authorization: Bearer <token>");
        }

        String token = authHeader.substring(7);

        try {
            var claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

            if (claims.getExpiration().before(new Date())) {
                return unauthorized(exchange, "Token expirado");
            }
            if (!"access".equals(claims.get("type", String.class))) {
                return unauthorized(exchange, "Tipo de token inválido");
            }

            String role = claims.get("role", String.class);
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.set(USER_ID_HEADER, claims.getSubject());
                        if (role != null) headers.set(USER_ROLE_HEADER, role);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "Token inválido: " + e.getMessage());
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        byte[] bytes = ("{\"error\":\"" + message.replace("\"", "'") + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -1; // se ejecuta antes que el filtro de enrutamiento
    }
}
