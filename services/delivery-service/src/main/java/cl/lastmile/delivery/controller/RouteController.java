package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteRequest;
import cl.lastmile.delivery.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /** Calcula y persiste el orden óptimo de entrega para todos los envíos de una fecha. */
    @PostMapping("/build")
    public ResponseEntity<List<RouteStop>> buildRoute(@Valid @RequestBody RouteRequest req) {
        List<RouteStop> route = routeService.buildRouteForDate(req.date(), req.depotLat(), req.depotLng());
        return ResponseEntity.ok(route);
    }
}
