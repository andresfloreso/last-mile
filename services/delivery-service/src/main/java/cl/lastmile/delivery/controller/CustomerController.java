package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.CustomerResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.DeclaredWindowRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.LocationRequest;
import cl.lastmile.delivery.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Locales", description = "Locales comerciales que reciben la mercadería")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR', 'DRIVER')")
    @Operation(summary = "Detalle de un local", description = "Incluye el tipo de local y su modulación declarada.")
    @ApiResponse(responseCode = "200", description = "Local encontrado")
    @ApiResponse(responseCode = "400", description = "customerId no es un identificador válido")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER, OPERATOR o DRIVER)")
    @ApiResponse(responseCode = "404", description = "El local no existe")
    public ResponseEntity<CustomerResponse> get(@Parameter(description = "Id del local") @PathVariable String customerId) {
        return ResponseEntity.ok(customerService.get(customerId));
    }

    @PutMapping("/{customerId}/declared-window")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @Operation(
            summary = "Reemplaza la modulación de un local",
            description = """
                    La modulación es el horario de recepción que el local declara (uno o dos tramos). \
                    Es distinta de la ventana real, que se aprende de los intentos de entrega. \
                    Enviar todos los campos en null deja al local sin modulación.""")
    @ApiResponse(responseCode = "200", description = "Modulación actualizada")
    @ApiResponse(responseCode = "400", description = "Tramos incompletos, inicio posterior al término o segundo tramo solapado")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @ApiResponse(responseCode = "404", description = "El local no existe")
    public ResponseEntity<CustomerResponse> updateDeclaredWindow(
            @Parameter(description = "Id del local") @PathVariable String customerId,
            @RequestBody DeclaredWindowRequest req) {
        return ResponseEntity.ok(customerService.updateDeclaredWindow(customerId, req));
    }

    @PutMapping("/{customerId}/location")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @Operation(
            summary = "Fija la ubicación de un local",
            description = "Reemplaza latitud y longitud por las que indica el usuario y quita la marca \"revisar\" (geocodeStatus pasa a PROVIDED).")
    @ApiResponse(responseCode = "200", description = "Ubicación actualizada")
    @ApiResponse(responseCode = "400", description = "Faltan coordenadas o están fuera de rango")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @ApiResponse(responseCode = "404", description = "El local no existe")
    public ResponseEntity<CustomerResponse> updateLocation(
            @Parameter(description = "Id del local") @PathVariable String customerId,
            @Valid @RequestBody LocationRequest req) {
        return ResponseEntity.ok(customerService.updateLocation(customerId, req));
    }
}
