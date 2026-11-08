package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final ShipmentService shipmentService;

    @GetMapping("/{trackingCode}")
    public TrackingResponse track(@PathVariable String trackingCode) {
        Shipment shipment = shipmentService.findByTrackingCode(trackingCode);
        return new TrackingResponse(
                shipment.getTrackingCode(),
                shipment.getStatus().name(),
                shipment.getScheduledDate().toString(),
                shipment.getRoutePosition()
        );
    }

    public record TrackingResponse(String trackingCode, String status, String scheduledDate, Integer routePosition) {}
}
