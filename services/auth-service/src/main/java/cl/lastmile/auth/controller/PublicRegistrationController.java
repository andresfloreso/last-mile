package cl.lastmile.auth.controller;

import cl.lastmile.auth.dto.AuthDtos.AuthResponse;
import cl.lastmile.auth.dto.AuthDtos.RegisterRequest;
import cl.lastmile.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro público (#44). LastMile es B2B: las cuentas las crea un ADMIN con
 * POST /api/users. Este endpoint solo existe si PUBLIC_REGISTRATION_ENABLED=true
 * (por defecto false); si no, POST /api/auth/register responde 404.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth", name = "public-registration-enabled", havingValue = "true")
public class PublicRegistrationController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Registro público (desactivado por defecto)",
            description = "Crea un usuario CLIENT. Solo existe con PUBLIC_REGISTRATION_ENABLED=true.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
