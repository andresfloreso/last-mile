package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.dto.DeliveryDtos.PlannedStopResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteRequest;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.service.RouteService;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import cl.lastmile.delivery.service.RouteTimingService;
import cl.lastmile.delivery.service.SavedRouteService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Rutas", description = "Ruta del día")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService routeService;
    private final RouteTimingService routeTimingService;
    private final SavedRouteService savedRouteService;
    private final LocalTime defaultDeparture;
    private final int defaultStopMinutes;

    public RouteController(RouteService routeService,
                           RouteTimingService routeTimingService,
                           SavedRouteService savedRouteService,
                           @Value("${routing.default-departure:08:00}") LocalTime defaultDeparture,
                           @Value("${routing.stop-minutes:10}") int defaultStopMinutes) {
        this.routeService = routeService;
        this.routeTimingService = routeTimingService;
        this.savedRouteService = savedRouteService;
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
                    Agrupa los envíos de la fecha en paradas, una por local (HU-30), y las ordena con el \
                    optimizador VRPTW de un camión (HU-28, Timefold): respeta la capacidad y la jornada del camión, \
                    y busca la menor cantidad de paradas fuera de su ventana real, después los menos km, \
                    con las paradas Primera y Última en los extremos. Si el solver falla o no hay solución \
                    factible, usa el orden por franja y cercanía (optimizer = NEAREST_NEIGHBOR y optimizerNote dice por qué). \
                    Después \
                    guarda el orden y calcula para cada parada la llegada, la espera hasta que abre su ventana real, \
                    el inicio de la entrega y la salida, con los tiempos por calle de OSRM y la duración de parada. \
                    Marca outsideWindow si la entrega empezaría cuando la ventana real ya cerró. \
                    Si OSRM no responde, los tiempos se estiman (timingSource = ESTIMATED).""")
    @ApiResponse(responseCode = "200", description = "Paradas en orden (una por local), con sus envíos, hora estimada y el tramo para el mapa")
    @ApiResponse(responseCode = "400", description = "Falta la fecha, stopMinutes fuera de 1–120, o el camión está dado de baja")
    @ApiResponse(responseCode = "404", description = "vehicleId no existe")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    public ResponseEntity<List<PlannedStopResponse>> buildRoute(@Valid @RequestBody RouteRequest req) {
        LocalTime departure = req.departureTime() != null ? req.departureTime() : defaultDeparture;
        int stopMinutes = req.stopMinutes() != null ? req.stopMinutes() : defaultStopMinutes;
        ConsolidatedRoute route = routeService.buildRouteForDate(req.date(), req.depotLat(), req.depotLng(),
                departure, stopMinutes, req.vehicleId());
        List<PlannedStopResponse> planned = routeTimingService.schedule(route.stops(), route.loads(),
                        req.depotLat(), req.depotLng(), departure, stopMinutes).stream()
                .map(p -> p.withOptimizer(route.optimizer(), route.note(), route.vehicle()))
                .toList();
        return ResponseEntity.ok().header(OPTIMIZER_HEADER, route.optimizer()).body(planned);
    }

    /**
     * La ruta guardada de una fecha, con hora estimada (HU-32). No reordena ni escribe: es lo que usa la
     * pantalla de rutas al abrirse. Para armar o rearmar la ruta está {@code POST /api/routes/build}.
     */
    @GetMapping
    @Operation(
            summary = "Ruta guardada del día, sin recalcular",
            description = """
                    Devuelve las paradas en el orden que guardó el último POST /api/routes/build, con la misma                     hora estimada por parada (llegada, espera, inicio, salida, outsideWindow y tramo para el mapa).                     No llama al optimizador ni cambia la posición o el estado de ningún envío. Los envíos sin                     posición (por ejemplo, importados después de armar la ruta) no aparecen; si la fecha no tiene                     ruta armada, devuelve una lista vacía. optimizer, optimizerNote y vehiclePlate vienen en null:                     no se guardan. Pueden verla ADMIN, DISPATCHER y OPERATOR; solo ADMIN y DISPATCHER la arman.""")
    @ApiResponse(responseCode = "200", description = "Paradas en el orden guardado (una por local), o lista vacía si no hay ruta armada")
    @ApiResponse(responseCode = "400", description = "Falta la fecha o el depósito, o stopMinutes fuera de 1–120")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    public List<PlannedStopResponse> savedRoute(
            @Parameter(description = "Fecha de la ruta", example = "2027-01-04")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Latitud del depósito") @RequestParam double depotLat,
            @Parameter(description = "Longitud del depósito") @RequestParam double depotLng,
            @Parameter(description = "Hora de salida (por defecto routing.default-departure)", example = "08:00")
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime departureTime,
            @Parameter(description = "Minutos por parada, 1–120 (por defecto routing.stop-minutes)")
            @RequestParam(required = false) Integer stopMinutes) {
        LocalTime departure = departureTime != null ? departureTime : defaultDeparture;
        if (stopMinutes != null && (stopMinutes < 1 || stopMinutes > 120)) {
            throw new BadRequestException("stopMinutes debe estar entre 1 y 120");
        }
        int minutes = stopMinutes != null ? stopMinutes : defaultStopMinutes;
        ConsolidatedRoute route = savedRouteService.savedRouteForDate(date);
        return routeTimingService.schedule(route.stops(), route.loads(), depotLat, depotLng, departure, minutes);
    }

    /** Quién ordenó la ruta (HU-28), también en un encabezado para no tener que leer las paradas. */
    static final String OPTIMIZER_HEADER = "X-Route-Optimizer";
}
