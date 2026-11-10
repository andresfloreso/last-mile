package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.PlannedStopResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteRequest;
import cl.lastmile.delivery.service.RouteService;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import cl.lastmile.delivery.service.RouteTimingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Rutas", description = "Ruta del día")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService routeService;
    private final RouteTimingService routeTimingService;
    private final LocalTime defaultDeparture;
    private final int defaultStopMinutes;

    public RouteController(RouteService routeService,
                           RouteTimingService routeTimingService,
                           @Value("${routing.default-departure:08:00}") LocalTime defaultDeparture,
                           @Value("${routing.stop-minutes:10}") int defaultStopMinutes) {
        this.routeService = routeService;
        this.routeTimingService = routeTimingService;
        this.defaultDeparture = defaultDeparture;
        this.defaultStopMinutes = defaultStopMinutes;
    }

    /**
     * Calcula y persiste el orden de entrega de todos los envíos de una fecha y le pone
     * hora estimada a cada parada (HU-14). El orden se guarda en una transacción; el horario
     * se calcula después, fuera de ella, para no retener la conexión mientras responde OSRM.
     */
    @PostMapping("/build")
    @Operation(
            summary = "Arma la ruta del día con hora estimada por parada",
            description = """
                    Agrupa los envíos de la fecha en paradas, una por local (HU-30), y las ordena \
                    (ventana real + cercanía, respetando las paradas Primera y Última); \
                    guarda el orden y calcula para cada parada la llegada, la espera hasta que abre su ventana real, \
                    el inicio de la entrega y la salida, con los tiempos por calle de OSRM y la duración de parada. \
                    Marca outsideWindow si la entrega empezaría cuando la ventana real ya cerró. \
                    Si OSRM no responde, los tiempos se estiman (timingSource = ESTIMATED).""")
    @ApiResponse(responseCode = "200", description = "Paradas en orden (una por local), con sus envíos, hora estimada y el tramo para el mapa")
    @ApiResponse(responseCode = "400", description = "Falta la fecha, o stopMinutes fuera de 1–120")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    public ResponseEntity<List<PlannedStopResponse>> buildRoute(@Valid @RequestBody RouteRequest req) {
        ConsolidatedRoute route = routeService.buildRouteForDate(req.date(), req.depotLat(), req.depotLng());
        LocalTime departure = req.departureTime() != null ? req.departureTime() : defaultDeparture;
        int stopMinutes = req.stopMinutes() != null ? req.stopMinutes() : defaultStopMinutes;
        return ResponseEntity.ok(routeTimingService.schedule(route.stops(), route.shipmentsByStop(),
                req.depotLat(), req.depotLng(), departure, stopMinutes));
    }
}
