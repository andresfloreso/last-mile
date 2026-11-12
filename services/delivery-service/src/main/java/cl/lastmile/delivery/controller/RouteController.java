package cl.lastmile.delivery.controller;

import cl.lastmile.delivery.algorithm.optimizer.UnloadTime;
import cl.lastmile.delivery.dto.DeliveryDtos.PlannedStopResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteOrderRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.RoutePreviewRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.RoutePreviewResponse;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteStateRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteStateResponse;
import cl.lastmile.delivery.model.Route;
import cl.lastmile.delivery.service.RequestValues;
import cl.lastmile.delivery.service.RouteStateService;
import jakarta.persistence.EntityNotFoundException;
import cl.lastmile.delivery.dto.DeliveryDtos.RouteSummaryResponse;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.service.RouteEditService;
import cl.lastmile.delivery.service.RouteEditService.ArrangedRoute;
import cl.lastmile.delivery.service.RouteService;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import cl.lastmile.delivery.service.RouteTimingService;
import cl.lastmile.delivery.service.RouteTimingService.TimedRoute;
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
    private final RouteEditService routeEditService;
    private final RouteStateService routeStateService;
    private final LocalTime defaultDeparture;
    private final int defaultStopMinutes;
    private final double perPackageMinutes;

    public RouteController(RouteService routeService,
                           RouteTimingService routeTimingService,
                           SavedRouteService savedRouteService,
                           RouteEditService routeEditService,
                           RouteStateService routeStateService,
                           @Value("${routing.default-departure:08:00}") LocalTime defaultDeparture,
                           @Value("${routing.stop-minutes:8}") int defaultStopMinutes,
                           @Value("${routing.unload.per-package-minutes:1.5}") double perPackageMinutes) {
        this.routeService = routeService;
        this.routeTimingService = routeTimingService;
        this.savedRouteService = savedRouteService;
        this.routeEditService = routeEditService;
        this.routeStateService = routeStateService;
        this.defaultDeparture = defaultDeparture;
        this.defaultStopMinutes = defaultStopMinutes;
        this.perPackageMinutes = perPackageMinutes;
    }

    /** Tiempo de descarga (HU-15): la base que pide la request (o la configurada) + bultos + ajuste del local. */
    private UnloadTime unload(Integer stopMinutes) {
        return new UnloadTime(stopMinutes != null ? stopMinutes : defaultStopMinutes, perPackageMinutes);
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
    @ApiResponse(responseCode = "409", description = "La ruta de la fecha ya está confirmada (HU-17): hay que volverla a borrador")
    public ResponseEntity<List<PlannedStopResponse>> buildRoute(@Valid @RequestBody RouteRequest req) {
        LocalTime departure = req.departureTime() != null ? req.departureTime() : defaultDeparture;
        UnloadTime unload = unload(req.stopMinutes());
        ConsolidatedRoute route = routeService.buildRouteForDate(req.date(), req.depotLat(), req.depotLng(),
                departure, unload, req.vehicleId());
        List<PlannedStopResponse> planned = routeTimingService.schedule(route.stops(), route.loads(),
                        req.depotLat(), req.depotLng(), departure, unload).stream()
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
                    Devuelve las paradas en el orden que guardó el último POST /api/routes/build, con la misma                     hora estimada por parada (llegada, espera, inicio, salida, outsideWindow y tramo para el mapa).                     No llama al optimizador ni cambia la posición o el estado de ningún envío. Los envíos sin                     posición (por ejemplo, importados después de armar la ruta) no aparecen; si la fecha no tiene                     ruta armada, devuelve una lista vacía. optimizer, optimizerNote y vehiclePlate salen de la ruta                     guardada (HU-17). Pueden verla ADMIN, DISPATCHER y OPERATOR; solo ADMIN y DISPATCHER la arman.""")
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
        ConsolidatedRoute route = savedRouteService.savedRouteForDate(date);
        List<PlannedStopResponse> planned = routeTimingService.schedule(route.stops(), route.loads(), depotLat, depotLng,
                departure, unload(stopMinutes));
        // HU-17: el optimizador y el camión ahora se guardan con la ruta.
        return routeStateService.state(date)
                .map(s -> planned.stream().map(p -> p.withOptimizer(s.optimizer(), s.optimizerNote(), s.vehiclePlate())).toList())
                .orElse(planned);
    }

    /** Estado de la ruta del día (HU-17). */
    @GetMapping("/state")
    @Operation(
            summary = "Estado de la ruta del día",
            description = """
                    Borrador, confirmada, en curso o cerrada, con el optimizador y el camión con que se armó y el \
                    conductor asignado. editable dice si se puede rearmar o reordenar (solo en borrador). Pueden \
                    verlo ADMIN, DISPATCHER y OPERATOR.""")
    @ApiResponse(responseCode = "200", description = "Estado de la ruta")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN, DISPATCHER u OPERATOR)")
    @ApiResponse(responseCode = "404", description = "La fecha no tiene ruta armada")
    public RouteStateResponse state(
            @Parameter(description = "Fecha de la ruta", example = "2026-11-12")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return routeStateService.state(date)
                .orElseThrow(() -> new EntityNotFoundException("La fecha " + date + " no tiene ruta armada"));
    }

    /** Confirma la ruta y la asigna a un conductor, o la mueve a otro estado (HU-17). */
    @PutMapping("/state")
    @Operation(
            summary = "Confirma la ruta y la asigna a un conductor, o cambia su estado",
            description = """
                    Cambios permitidos: DRAFT → CONFIRMED (exige driverId: el conductor que la va a hacer), \
                    CONFIRMED → DRAFT (vuelve a borrador para cambiarla; libera al conductor), CONFIRMED → IN_PROGRESS \
                    y IN_PROGRESS → CLOSED. Confirmar una ruta ya confirmada cambia el conductor. Una ruta que no está \
                    en borrador no se rearma (POST /api/routes/build) ni se reordena (PUT /api/routes/order): \
                    responden 409.""")
    @ApiResponse(responseCode = "200", description = "Estado nuevo de la ruta")
    @ApiResponse(responseCode = "400", description = "Estado desconocido, o falta driverId para confirmar")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    @ApiResponse(responseCode = "404", description = "La fecha no tiene ruta armada")
    @ApiResponse(responseCode = "409", description = "Cambio de estado no permitido (por ejemplo, de cerrada a borrador)")
    public RouteStateResponse changeState(@Valid @RequestBody RouteStateRequest req) {
        Route.Status target = RequestValues.enumValue(Route.Status.class, req.status(), "status");
        return routeStateService.changeStatus(req.date(), target,
                req.driverId() == null || req.driverId().isBlank() ? null : RequestValues.uuid(req.driverId(), "driverId"),
                req.driverName());
    }

    /**
     * Vista previa del armado manual (HU-15): la ruta guardada en el orden que pide el despachador, con
     * hora estimada e indicadores. No escribe nada.
     */
    @PostMapping("/preview")
    @Operation(
            summary = "Vista previa de la ruta en otro orden (armado manual)",
            description = """
                    Recalcula la hora estimada de cada parada con el orden pedido, sin guardarlo, y devuelve los \
                    indicadores de la ruta: km (con la vuelta a la bodega), hora de vuelta, paradas fuera de \
                    ventana real y minutos de espera. order debe traer exactamente las paradas de la ruta \
                    guardada (por su shipmentId). Con reoptimize = true ("Reoptimizar el resto"), las paradas \
                    de pinned quedan en su posición y el optimizador ordena las demás en los lugares libres. \
                    Con el orden guardado, sirve de línea base para comparar ("+1,2 km vs. sistema").""")
    @ApiResponse(responseCode = "200", description = "Paradas en el orden pedido (o reoptimizado), con hora estimada e indicadores")
    @ApiResponse(responseCode = "400", description = "order vacío o con paradas repetidas, una parada fijada que no está en la ruta, o stopMinutes fuera de 1–120")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    @ApiResponse(responseCode = "409", description = "La fecha no tiene ruta armada, o la ruta cambió (order no trae las mismas paradas)")
    public RoutePreviewResponse preview(@Valid @RequestBody RoutePreviewRequest req) {
        LocalTime departure = req.departureTime() != null ? req.departureTime() : defaultDeparture;
        UnloadTime unload = unload(req.stopMinutes());
        ArrangedRoute arranged = routeEditService.arrange(req.date(), req.order(), req.pinned(), req.reoptimize(),
                req.depotLat(), req.depotLng(), departure, unload, req.vehicleId());
        TimedRoute timed = routeTimingService.plan(arranged.route().stops(), arranged.route().loads(),
                req.depotLat(), req.depotLng(), departure, unload, true);
        return new RoutePreviewResponse(timed.stops(), summary(timed), arranged.optimizer(), arranged.note());
    }

    /**
     * Guarda el orden que armó el despachador (HU-15). La hora estimada se vuelve a pedir con
     * {@code GET /api/routes}.
     */
    @PutMapping("/order")
    @Operation(
            summary = "Guarda el orden de la ruta (armado manual)",
            description = """
                    Cada envío toma la posición de su parada en order. No llama al optimizador ni cambia el estado \
                    de los envíos. order debe traer exactamente las paradas de la ruta guardada (por su shipmentId).""")
    @ApiResponse(responseCode = "204", description = "Orden guardado")
    @ApiResponse(responseCode = "400", description = "order vacío o con paradas repetidas")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso (solo ADMIN o DISPATCHER)")
    @ApiResponse(responseCode = "409", description = "La fecha no tiene ruta armada, la ruta cambió (order no trae las mismas paradas) o está confirmada (HU-17)")
    public ResponseEntity<Void> saveOrder(@Valid @RequestBody RouteOrderRequest req) {
        routeEditService.saveOrder(req.date(), req.order());
        return ResponseEntity.noContent().build();
    }

    static RouteSummaryResponse summary(TimedRoute timed) {
        long meters = 0;
        int outside = 0;
        int wait = 0;
        for (PlannedStopResponse s : timed.stops()) {
            if (s.legDistanceMeters() != null) meters += s.legDistanceMeters();
            if (s.outsideWindow()) outside++;
            wait += s.waitMinutes();
        }
        RouteTimingService.ReturnLeg back = timed.returnLeg();
        if (back != null) meters += back.distanceMeters();
        return new RouteSummaryResponse((int) meters, back == null ? null : back.arrival(),
                back == null ? 0 : back.dayOffset(), outside, wait, back == null ? List.of() : back.path());
    }

    /** Quién ordenó la ruta (HU-28), también en un encabezado para no tener que leer las paradas. */
    static final String OPTIMIZER_HEADER = "X-Route-Optimizer";
}
