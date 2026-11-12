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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestión de usuarios reservada a administradores (HU-05). Es el único camino
 * para crear cuentas con un rol distinto de CLIENT. Despacho además lista los
 * conductores para asignarles la ruta (HU-17).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestión de usuarios (ADMIN) y conductores para asignar rutas (ADMIN y DISPATCHER)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AuthService authService;

    @GetMapping("/drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    @Operation(
            summary = "Conductores activos",
            description = "Usuarios con rol DRIVER habilitados, por nombre. Despacho elige uno al confirmar la ruta del día (HU-17).")
    @ApiResponse(responseCode = "200", description = "Conductores (puede ser una lista vacía)")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN ni DISPATCHER")
    public List<UserResponse> drivers() {
        return authService.drivers();
    }

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
