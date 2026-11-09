package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.*;
import cl.lastmile.delivery.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createOrGetCustomer(req));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody ShipmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createShipment(req));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @GetMapping
    public ResponseEntity<List<ShipmentResponse>> listByDate(
            @RequestParam("date") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(shipmentService.findByDate(date));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR', 'DRIVER')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER, OPERATOR o DRIVER)")
    @PostMapping("/attempts")
    public ResponseEntity<DeliveryAttemptResponse> registerAttempt(@Valid @RequestBody DeliveryAttemptRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.registerAttempt(req));
    }

    /** Edita tipo, paquetes, orden, etiqueta o notas de una parada — panel de detalle tipo Spoke. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @PatchMapping("/{shipmentId}")
    public ResponseEntity<ShipmentResponse> updateShipment(
            @PathVariable String shipmentId,
            @RequestBody ShipmentUpdateRequest req
    ) {
        return ResponseEntity.ok(shipmentService.updateShipment(shipmentId, req));
    }

    /** "Duplicar parada" — crea una copia con un nuevo tracking code. */
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @PostMapping("/{shipmentId}/duplicate")
    public ResponseEntity<ShipmentResponse> duplicateShipment(
            @PathVariable String shipmentId,
            @RequestParam String newTrackingCode
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.duplicateShipment(shipmentId, newTrackingCode));
    }

    /** "Eliminar parada". */
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER', 'OPERATOR')")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @DeleteMapping("/{shipmentId}")
    public ResponseEntity<Void> deleteShipment(@PathVariable String shipmentId) {
        shipmentService.deleteShipment(shipmentId);
        return ResponseEntity.noContent().build();
    }
}
