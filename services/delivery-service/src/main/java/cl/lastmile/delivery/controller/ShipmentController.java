package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.*;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping("/customers")
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createOrGetCustomer(req));
    }

    @PostMapping
    public ResponseEntity<Shipment> createShipment(@Valid @RequestBody ShipmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.createShipment(req));
    }

    @GetMapping
    public ResponseEntity<List<Shipment>> listByDate(
            @RequestParam("date") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(shipmentService.findByDate(date));
    }

    @PostMapping("/attempts")
    public ResponseEntity<DeliveryAttempt> registerAttempt(@Valid @RequestBody DeliveryAttemptRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shipmentService.registerAttempt(req));
    }

    /** Edita tipo, paquetes, orden, etiqueta o notas de una parada — panel de detalle tipo Spoke. */
    @PatchMapping("/{shipmentId}")
    public ResponseEntity<Shipment> updateShipment(
            @PathVariable String shipmentId,
            @RequestBody ShipmentUpdateRequest req
    ) {
        return ResponseEntity.ok(shipmentService.updateShipment(shipmentId, req));
    }

    /** "Duplicar parada" — crea una copia con un nuevo tracking code. */
    @PostMapping("/{shipmentId}/duplicate")
    public ResponseEntity<Shipment> duplicateShipment(
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
