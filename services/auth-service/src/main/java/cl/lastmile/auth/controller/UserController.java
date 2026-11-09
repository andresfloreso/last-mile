package cl.lastmile.auth.controller;

import cl.lastmile.auth.dto.AuthDtos.CreateUserRequest;
import cl.lastmile.auth.dto.AuthDtos.UserResponse;
import cl.lastmile.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de usuarios reservada a administradores (HU-05). Es el único camino
 * para crear cuentas con un rol distinto de CLIENT.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestión de usuarios (solo ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Crear un usuario con cualquier rol",
            description = "Solo un ADMIN crea usuarios y asigna roles. El registro público está desactivado por defecto (#44).")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN")
    @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese email")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUserAsAdmin(request));
    }
}
