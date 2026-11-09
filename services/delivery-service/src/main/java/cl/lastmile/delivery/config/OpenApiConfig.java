package cl.lastmile.delivery.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** OpenAPI del servicio: /v3/api-docs (JSON) y /swagger-ui.html (UI). */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "LastMile delivery-service",
        version = "1.0.0",
        description = "Locales, envíos, intentos de entrega, ventanas horarias y rutas"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
