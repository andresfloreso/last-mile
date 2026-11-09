package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.*;
import cl.lastmile.delivery.service.ShipmentFilter;
import cl.lastmile.delivery.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Tag(name = "Envíos", description = "Envíos programados a los locales")
@SecurityRequirement(name = "bearerAuth")
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createOrGetCustomer(req));
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody ShipmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createShipment(req));
    }

    @GetMapping
    @Operation(
            summary = "Lista y filtra envíos (paginado)",
            description = """
                    Todos los filtros son opcionales y se combinan. Orden: fecha más reciente primero; \
                    dentro de un día, por posición en la ruta y luego por tracking. \
                    La comuna y la búsqueda no distinguen mayúsculas ni tildes ("nunoa" encuentra "Ñuñoa").""")
    @ApiResponse(responseCode = "200", description = "Página de envíos, cada uno con su local")
    @ApiResponse(responseCode = "400", description = "Fecha o estado inválido, 'date' combinado con 'from'/'to', rango invertido, o page/size fuera de rango")
    public ResponseEntity<PageResponse<ShipmentResponse>> list(
            @Parameter(description = "Un solo día programado (atajo de from = to = date)", example = "2026-12-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Desde este día programado (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Hasta este día programado (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Uno o más estados (status=PENDING&status=ROUTED o status=PENDING,ROUTED)",
                    array = @ArraySchema(schema = @Schema(allowableValues = {
                            "PENDING", "ROUTED", "OUT_FOR_DELIVERY", "DELIVERED", "REJECTED", "RESCHEDULED"})))
            @RequestParam(required = false) List<String> status,
            @Parameter(description = "Comuna del local", example = "Providencia")
            @RequestParam(required = false) String commune,
            @Parameter(description = "Busca en el nombre del local, su dirección, el tracking o el código externo", example = "roble")
            @RequestParam(required = false) String q,
            @Parameter(description = "Página, desde 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página, de 1 a " + ShipmentService.MAX_PAGE_SIZE)
            @RequestParam(defaultValue = "20") int size
    ) {
        ShipmentFilter filter = ShipmentFilter.of(date, from, to, status, commune, q);
        return ResponseEntity.ok(shipmentService.search(filter, page, size));
    }

    @PostMapping("/attempts")
    public ResponseEntity<DeliveryAttemptResponse> registerAttempt(@Valid @RequestBody DeliveryAttemptRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.registerAttempt(req));
    }

    /** Edita tipo, paquetes, orden, etiqueta o notas de una parada — panel de detalle tipo Spoke. */
    @PatchMapping("/{shipmentId}")
    public ResponseEntity<ShipmentResponse> updateShipment(
            @PathVariable String shipmentId,
            @RequestBody ShipmentUpdateRequest req
    ) {
        return ResponseEntity.ok(shipmentService.updateShipment(shipmentId, req));
    }

    /** "Duplicar parada" — crea una copia con un nuevo tracking code. */
    @PostMapping("/{shipmentId}/duplicate")
    public ResponseEntity<ShipmentResponse> duplicateShipment(
            @PathVariable String shipmentId,
            @RequestParam String newTrackingCode
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.duplicateShipment(shipmentId, newTrackingCode));
    }

    /** "Eliminar parada". */
    @DeleteMapping("/{shipmentId}")
    public ResponseEntity<Void> deleteShipment(@PathVariable String shipmentId) {
        shipmentService.deleteShipment(shipmentId);
        return ResponseEntity.noContent().build();
    }
}
