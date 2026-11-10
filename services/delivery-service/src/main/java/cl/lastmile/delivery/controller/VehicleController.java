package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.VehicleRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.VehicleResponse;
import cl.lastmile.delivery.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehículos", description = "Camiones de la distribuidora, con capacidad y jornada (HU-29)")
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @Operation(summary = "Lista los vehículos", description = "Ordenados por patente. Por defecto, solo los activos.")
    @ApiResponse(responseCode = "200", description = "Vehículos")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    public ResponseEntity<List<VehicleResponse>> list(
            @Parameter(description = "true incluye los dados de baja")
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(vehicleService.list(includeInactive));
    }

    @GetMapping("/{vehicleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @Operation(summary = "Detalle de un vehículo")
    @ApiResponse(responseCode = "200", description = "Vehículo encontrado")
    @ApiResponse(responseCode = "400", description = "vehicleId no es un identificador válido")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @ApiResponse(responseCode = "404", description = "El vehículo no existe")
    public ResponseEntity<VehicleResponse> get(@Parameter(description = "Id del vehículo") @PathVariable String vehicleId) {
        return ResponseEntity.ok(vehicleService.get(vehicleId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    @Operation(summary = "Registra un vehículo",
            description = "La patente se guarda en mayúsculas y sin espacios, y no se puede repetir.")
    @ApiResponse(responseCode = "201", description = "Vehículo creado")
    @ApiResponse(responseCode = "400", description = "Falta un dato, capacidad no positiva o jornada que termina antes de empezar")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    @ApiResponse(responseCode = "409", description = "Ya existe un vehículo con esa patente")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleService.create(req));
    }

    @PutMapping("/{vehicleId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    @Operation(summary = "Reemplaza los datos de un vehículo",
            description = "Con active = false lo da de baja: deja de usarse en rutas nuevas, pero no se borra.")
    @ApiResponse(responseCode = "200", description = "Vehículo actualizado")
    @ApiResponse(responseCode = "400", description = "Falta un dato, capacidad no positiva o jornada que termina antes de empezar")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    @ApiResponse(responseCode = "404", description = "El vehículo no existe")
    @ApiResponse(responseCode = "409", description = "Ya existe otro vehículo con esa patente")
    public ResponseEntity<VehicleResponse> update(@Parameter(description = "Id del vehículo") @PathVariable String vehicleId,
                                                  @Valid @RequestBody VehicleRequest req) {
        return ResponseEntity.ok(vehicleService.update(vehicleId, req));
    }
}
