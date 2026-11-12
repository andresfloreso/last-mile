package cl.lastmile.delivery.dto;

import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeclaredWindow;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Vehicle;
import cl.lastmile.delivery.service.StopConsolidator;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class DeliveryDtos {

    public record CustomerRequest(
            @NotBlank String fullName,
            String phone,
            @NotBlank String address,
            String commune,
            Double latitude,
            Double longitude,
            String externalCode,
            @Schema(description = "Tipo de local", allowableValues = {"BOTILLERIA", "MINIMARKET", "BAR", "RESTAURANT", "OTRO"})
            String businessType,
            @Schema(description = "Modulación: inicio del horario de recepción declarado", example = "09:00", type = "string")
            LocalTime declaredWindowStart,
            @Schema(description = "Modulación: término del horario de recepción declarado", example = "12:00", type = "string")
            LocalTime declaredWindowEnd,
            @Schema(description = "Modulación, segundo tramo opcional: inicio", example = "16:00", type = "string")
            LocalTime declaredWindow2Start,
            @Schema(description = "Modulación, segundo tramo opcional: término", example = "18:00", type = "string")
            LocalTime declaredWindow2End
    ) {
        public DeclaredWindow declaredWindow() {
            return new DeclaredWindow(declaredWindowStart, declaredWindowEnd, declaredWindow2Start, declaredWindow2End);
        }
    }

    /**
     * Reemplaza la modulación completa de un local (HU-25). Enviar todo en
     * null deja al local sin modulación.
     */
    public record DeclaredWindowRequest(
            @Schema(description = "Inicio del primer tramo", example = "09:00", type = "string") LocalTime start,
            @Schema(description = "Término del primer tramo", example = "12:00", type = "string") LocalTime end,
            @Schema(description = "Inicio del segundo tramo (opcional)", example = "16:00", type = "string") LocalTime start2,
            @Schema(description = "Término del segundo tramo (opcional)", example = "18:00", type = "string") LocalTime end2
    ) {
        public DeclaredWindow toDeclaredWindow() {
            return new DeclaredWindow(start, end, start2, end2);
        }
    }

    /** Coordenadas fijadas a mano para un local (resuelve la marca "revisar" de HU-09). */
    public record LocationRequest(
            @NotNull @Schema(example = "-33.4263") Double latitude,
            @NotNull @Schema(example = "-70.6105") Double longitude
    ) {}

    public record ShipmentRequest(
            @NotBlank String trackingCode,
            @NotNull String customerId,
            @NotNull LocalDate scheduledDate,
            String source,
            String notes,
            String stopType,        // DELIVERY | PICKUP (opcional, default DELIVERY)
            Integer packageCount,   // opcional, default 1
            String orderPreference, // FIRST | AUTO | LAST (opcional, default AUTO)
            String label,
            @Positive @Schema(description = "Peso total del envío en kg (HU-29). Si falta: shipments.default-weight-kg (10)", example = "24.5")
            Double weightKg,
            @Positive @Schema(description = "Volumen total del envío en m³ (HU-29). Si falta: shipments.default-volume-m3 (0,05)", example = "0.08")
            Double volumeM3
    ) {}

    public record DeliveryAttemptRequest(
            @NotNull String shipmentId,
            @NotNull String customerId,
            Instant attemptedAt,
            @NotBlank String outcome, // ACCEPTED | REJECTED_NOT_HOME | REJECTED_REFUSED | RESCHEDULED_BY_CUSTOMER
            String rejectionReason
    ) {}

    public record RouteRequest(
            @NotNull LocalDate date,
            double depotLat,
            double depotLng,
            @Schema(description = "Hora de salida del depósito (HU-14). Por defecto routing.default-departure (08:00)",
                    type = "string", example = "08:00")
            LocalTime departureTime,
            @Schema(description = "Minutos de entrega en cada local (HU-14). Por defecto routing.stop-minutes (10)",
                    example = "10", minimum = "1", maximum = "120")
            @Min(1) @Max(120) Integer stopMinutes,
            @Schema(description = "Camión de la ruta (HU-28): su capacidad y su jornada son restricciones duras. "
                    + "Si falta, se usa el camión activo de mayor capacidad; sin camiones, la ruta no tiene esos límites")
            String vehicleId
    ) {}

    @Schema(description = "Ruta del día en otro orden, para la vista previa del armado manual (HU-15). No guarda nada")
    public record RoutePreviewRequest(
            @NotNull LocalDate date,
            double depotLat,
            double depotLng,
            @Schema(description = "Hora de salida del depósito. Por defecto routing.default-departure (08:00)", type = "string", example = "08:00")
            LocalTime departureTime,
            @Schema(description = "Base del tiempo de descarga en cada parada (HU-15). Por defecto routing.stop-minutes (8)", example = "8",
                    minimum = "1", maximum = "120")
            @Min(1) @Max(120) Integer stopMinutes,
            @NotNull @Schema(description = "Paradas en el orden pedido, por su shipmentId (el envío representante). Deben ser exactamente las de la ruta guardada")
            List<UUID> order,
            @Schema(description = "Paradas fijadas con candado: al reoptimizar quedan en su posición")
            List<UUID> pinned,
            @Schema(description = "true: el optimizador ordena las paradas no fijadas en los lugares libres (\"Reoptimizar el resto\")")
            boolean reoptimize,
            @Schema(description = "Camión para reoptimizar (capacidad y jornada). Por defecto, el activo de mayor capacidad")
            String vehicleId
    ) {}

    @Schema(description = "Orden de la ruta que guarda el despachador (HU-15)")
    public record RouteOrderRequest(
            @NotNull LocalDate date,
            @NotNull @Schema(description = "Paradas en el orden nuevo, por su shipmentId. Deben ser exactamente las de la ruta guardada")
            List<UUID> order
    ) {}

    @Schema(description = "Ruta con hora estimada e indicadores (HU-15)")
    public record RoutePreviewResponse(
            @Schema(description = "Paradas en el orden pedido (o reoptimizado), con hora estimada")
            List<PlannedStopResponse> stops,
            RouteSummaryResponse summary,
            @Schema(description = "Quién ordenó las paradas libres al reoptimizar; null si no se reoptimizó",
                    allowableValues = {"VRPTW", "NEAREST_NEIGHBOR"})
            String optimizer,
            @Schema(description = "Aviso del optimizador (fallback); null si no hubo")
            String optimizerNote
    ) {}

    @Schema(description = "Indicadores de la ruta (HU-15)")
    public record RouteSummaryResponse(
            @Schema(description = "Metros de la ruta, incluida la vuelta a la bodega", example = "48500") int distanceMeters,
            @JsonFormat(pattern = "HH:mm") @Schema(description = "Hora de vuelta a la bodega; null si ninguna parada tiene coordenadas",
                    type = "string", example = "19:17")
            LocalTime returnTime,
            @Schema(description = "1 si la vuelta a la bodega es al día siguiente", example = "0") int returnDayOffset,
            @Schema(description = "Paradas cuya entrega empezaría fuera de su ventana real", example = "2") int outsideWindowStops,
            @Schema(description = "Minutos de espera sumados, hasta que abre la ventana real de cada parada", example = "141") int waitMinutes,
            @Schema(description = "Tramo de vuelta a la bodega como pares [lat, lng]")
            List<double[]> returnPath
    ) {}

    @Schema(description = "Ajuste del tiempo de descarga de un local (HU-15)")
    public record UnloadAdjustmentRequest(
            @NotNull @Min(0) @Max(120) @Schema(description = "Minutos que se suman a la descarga en este local; 0 lo quita", example = "10")
            Integer extraMinutes,
            @Size(max = 120) @Schema(description = "Por qué", example = "estacionamiento difícil")
            String note
    ) {}

    @Schema(description = "Parada de la ruta con su hora estimada (HU-14)")
    public record PlannedStopResponse(
            UUID shipmentId,
            String customerName,
            String address,
            @Schema(description = "Posición en la ruta, desde 1") int position,
            @Schema(description = "Inicio de la ventana real del local", type = "string", example = "12:00:00") LocalTime windowStart,
            @Schema(description = "Fin de la ventana real del local", type = "string", example = "14:00:00") LocalTime windowEnd,
            double confidence,
            List<Integer> hoursToAvoid,
            String stopType,
            int packageCount,
            @Schema(allowableValues = {"FIRST", "AUTO", "LAST"}) String orderPreference,
            String label,
            Double latitude,
            Double longitude,
            @JsonFormat(pattern = "HH:mm") @Schema(description = "Hora estimada de llegada del camión", type = "string", example = "11:42")
            LocalTime estimatedArrival,
            @JsonFormat(pattern = "HH:mm") @Schema(description = "Inicio estimado de la entrega: la llegada, o la apertura de la ventana si el camión llega antes", type = "string", example = "12:00")
            LocalTime serviceStart,
            @JsonFormat(pattern = "HH:mm") @Schema(description = "Salida estimada del local", type = "string", example = "12:10")
            LocalTime estimatedDeparture,
            @Schema(description = "Minutos de espera hasta que abre la ventana real", example = "18") int waitMinutes,
            @Schema(description = "Minutos de viaje desde la parada anterior (o el depósito); null si el local no tiene coordenadas", example = "14")
            Integer travelMinutes,
            @Schema(description = "Metros desde la parada anterior; null si el local no tiene coordenadas", example = "5230")
            Integer legDistanceMeters,
            @Schema(description = "true si la entrega empezaría a la hora de cierre de la ventana real o después")
            boolean outsideWindow,
            @Schema(description = "Tramo desde la parada anterior como pares [lat, lng], para la polilínea del mapa")
            List<double[]> path,
            @Schema(description = "OSRM: tiempos por calle; ESTIMATED: OSRM no respondió, línea recta a velocidad urbana; NO_LOCATION: el local no tiene coordenadas",
                    allowableValues = {"OSRM", "ESTIMATED", "NO_LOCATION"})
            String timingSource,
            @Schema(description = "0 si la llegada es el día programado; 1 si la ruta no alcanza y llega al día siguiente", example = "0")
            int dayOffset,
            @Schema(description = "Envíos de la parada (HU-30): todos los del mismo local en la fecha. El primero es shipmentId")
            List<UUID> shipmentIds,
            @Schema(description = "Cantidad de envíos de la parada (HU-30)", example = "2")
            int shipmentCount,
            @Schema(description = "Peso de la parada en kg: suma de sus envíos (HU-29)", example = "42.5")
            Double weightKg,
            @Schema(description = "Volumen de la parada en m³: suma de sus envíos (HU-29)", example = "0.12")
            Double volumeM3,
            @Schema(description = "Quién ordenó la ruta (HU-28): VRPTW (Timefold) o NEAREST_NEIGHBOR (fallback o configurado)",
                    allowableValues = {"VRPTW", "NEAREST_NEIGHBOR"})
            String optimizer,
            @Schema(description = "Por qué se usó el fallback (solver con error o sin solución factible para la capacidad o la jornada del camión); null si no hubo aviso")
            String optimizerNote,
            @Schema(description = "Patente del camión con que se armó la ruta; null si no hay camiones registrados", example = "KXPT-21")
            String vehiclePlate,
            @Schema(description = "Minutos de descarga en la parada (HU-15): base + minutos por bulto + ajuste del local", example = "17")
            int serviceMinutes,
            @Schema(description = "Ajuste de descarga del local en minutos (HU-15); 0 si no tiene", example = "10")
            int unloadExtraMinutes,
            @Schema(description = "Por qué el local tiene ajuste de descarga (HU-15); null si no tiene", example = "estacionamiento difícil")
            String unloadNote
    ) {
        /** La misma parada con quién ordenó la ruta y con qué camión (HU-28). */
        public PlannedStopResponse withOptimizer(String optimizer, String optimizerNote, String vehiclePlate) {
            return new PlannedStopResponse(shipmentId, customerName, address, position, windowStart, windowEnd,
                    confidence, hoursToAvoid, stopType, packageCount, orderPreference, label, latitude, longitude,
                    estimatedArrival, serviceStart, estimatedDeparture, waitMinutes, travelMinutes, legDistanceMeters,
                    outsideWindow, path, timingSource, dayOffset, shipmentIds, shipmentCount, weightKg, volumeM3,
                    optimizer, optimizerNote, vehiclePlate, serviceMinutes, unloadExtraMinutes, unloadNote);
        }

        public static PlannedStopResponse of(RouteOptimizationService.RouteStop s,
                                             LocalTime arrival, LocalTime serviceStart, LocalTime departure,
                                             int waitMinutes, Integer travelMinutes, Integer legDistanceMeters,
                                             boolean outsideWindow, List<double[]> path, String timingSource,
                                             int dayOffset, StopConsolidator.StopLoad load, int serviceMinutes) {
            return new PlannedStopResponse(s.shipmentId(), s.customerName(), s.address(), s.position(),
                    s.windowStart(), s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(),
                    s.packageCount(), s.orderPreference(), s.label(), s.latitude(), s.longitude(),
                    arrival, serviceStart, departure, waitMinutes, travelMinutes, legDistanceMeters,
                    outsideWindow, path, timingSource, dayOffset, load.shipmentIds(), load.shipmentIds().size(),
                    load.weightKg(), load.volumeM3(), null, null, null, serviceMinutes,
                    load.unloadExtraMinutes(), load.unloadNote());
        }
    }

    /** Edición parcial de una parada — refleja el panel de detalle de la app de referencia. */
    public record ShipmentUpdateRequest(
            String stopType,
            Integer packageCount,
            String orderPreference,
            String label,
            String notes,
            @Schema(description = "Peso total del envío en kg (HU-29), mayor que 0", example = "24.5") Double weightKg,
            @Schema(description = "Volumen total del envío en m³ (HU-29), mayor que 0", example = "0.08") Double volumeM3
    ) {}

    public record BulkImportResult(
            int totalRows,
            int created,
            int skipped,
            java.util.List<String> errors
    ) {}

    // -----------------------------------------------------------------
    // Respuestas de la API. Los controllers nunca devuelven entidades JPA:
    // las relaciones LAZY (p.ej. Shipment.customer) son proxies de
    // Hibernate que Jackson no puede serializar (issue #11).
    // -----------------------------------------------------------------

    public record CustomerResponse(
            UUID id,
            String fullName,
            String phone,
            String address,
            String commune,
            Double latitude,
            Double longitude,
            String externalCode,
            String businessType,
            @Schema(description = "Modulación declarada; null si el local no declaró horario")
            DeclaredWindowResponse declaredWindow,
            @Schema(description = "Origen de las coordenadas: PROVIDED (usuario), GEOCODED (Nominatim, precisa) o REVIEW (aproximada o no encontrada: revisar)",
                    allowableValues = {"PROVIDED", "GEOCODED", "REVIEW"})
            String geocodeStatus,
            @Schema(description = "Minutos que se suman a la descarga en este local (HU-15); 0 si no tiene ajuste", example = "10")
            int unloadExtraMinutes,
            @Schema(description = "Por qué el local tiene ajuste de descarga (HU-15)", example = "estacionamiento difícil")
            String unloadNote
    ) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getAddress(),
                    c.getCommune(), c.getLatitude(), c.getLongitude(), c.getExternalCode(),
                    name(c.getBusinessType()), DeclaredWindowResponse.from(c.declaredWindow()),
                    name(c.getGeocodeStatus()), c.getUnloadExtraMinutes(), c.getUnloadNote());
        }
    }

    public record DeclaredWindowResponse(
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "09:00") LocalTime start,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:00") LocalTime end,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "16:00") LocalTime start2,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "18:00") LocalTime end2
    ) {
        static DeclaredWindowResponse from(DeclaredWindow w) {
            return w.isEmpty() ? null : new DeclaredWindowResponse(w.start(), w.end(), w.start2(), w.end2());
        }
    }

    @Schema(description = "Modulación declarada vs. ventana real aprendida de un local (HU-11)")
    public record TimeWindowResponse(
            UUID customerId,
            String customerName,
            @Schema(description = "Modulación declarada; null si el local no declaró horario")
            DeclaredWindowResponse declaredWindow,
            @Schema(description = "Ventana real que usa la ruta para ordenar este local")
            RealWindowResponse realWindow,
            @Schema(description = "Explicación en una frase", example = "Recibió 8 de 10 visitas entre 12:00 y 14:00")
            String summary,
            @Schema(description = "Intentos de entrega registrados", example = "25") int totalAttempts,
            @Schema(description = "Intentos aceptados", example = "20") int acceptedAttempts,
            @Schema(description = "Intentos no aceptados (cerrado, rechazo o pidió otro horario)", example = "5") int rejectedAttempts,
            @Schema(description = "Horas (8 a 19) en que el local rechaza más de lo que recibe", example = "[9, 10]")
            List<Integer> hoursToAvoid,
            @Schema(description = "24 casilleros, de 0 a 23 h (hora de Santiago)")
            List<HourBucketResponse> histogram
    ) {
        public static TimeWindowResponse from(Customer customer, CustomerTimeWindowProfile profile, String summary) {
            List<HourBucketResponse> histogram = profile.histogram().stream()
                    .map(b -> new HourBucketResponse(b.hour(), b.accepted(), b.rejected()))
                    .toList();
            int accepted = histogram.stream().mapToInt(HourBucketResponse::accepted).sum();
            return new TimeWindowResponse(
                    customer.getId(),
                    customer.getFullName(),
                    DeclaredWindowResponse.from(customer.declaredWindow()),
                    new RealWindowResponse(
                            LocalTime.of(profile.recommendedStartHour(), 0),
                            LocalTime.of(profile.recommendedEndHour(), 0),
                            profile.acceptedInWindow(),
                            profile.attemptsInWindow(),
                            profile.learned() ? "LEARNED" : "DEFAULT"),
                    summary,
                    profile.sampleSize(),
                    accepted,
                    profile.sampleSize() - accepted,
                    profile.hoursToAvoid(),
                    histogram);
        }
    }

    public record RealWindowResponse(
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:00") LocalTime start,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "14:00") LocalTime end,
            @Schema(description = "Visitas aceptadas dentro de la ventana (la X de \"X de Y visitas\")", example = "8")
            int acceptedVisits,
            @Schema(description = "Visitas dentro de la ventana (la Y)", example = "10")
            int visits,
            @Schema(description = "LEARNED: aprendida de los intentos; DEFAULT: sin intentos, horario comercial por defecto",
                    allowableValues = {"LEARNED", "DEFAULT"})
            String basis
    ) {
    }

    public record HourBucketResponse(
            @Schema(description = "Hora del día, 0 a 23", example = "12") int hour,
            @Schema(description = "Visitas aceptadas en esa hora", example = "4") int accepted,
            @Schema(description = "Visitas no aceptadas en esa hora", example = "1") int rejected
    ) {
    }

    public record ShipmentResponse(
            UUID id,
            String trackingCode,
            LocalDate scheduledDate,
            String status,
            String source,
            String notes,
            Integer routePosition,
            String stopType,
            Integer packageCount,
            String orderPreference,
            String label,
            Instant createdAt,
            CustomerResponse customer,
            @Schema(description = "Peso total del envío en kg (HU-29)", example = "24.5") Double weightKg,
            @Schema(description = "Volumen total del envío en m³ (HU-29)", example = "0.08") Double volumeM3
    ) {
        /** Debe invocarse dentro de una transacción: inicializa el cliente LAZY. */
        public static ShipmentResponse from(Shipment s) {
            return new ShipmentResponse(
                    s.getId(),
                    s.getTrackingCode(),
                    s.getScheduledDate(),
                    name(s.getStatus()),
                    s.getSource(),
                    s.getNotes(),
                    s.getRoutePosition(),
                    name(s.getStopType()),
                    s.getPackageCount(),
                    name(s.getOrderPreference()),
                    s.getLabel(),
                    s.getCreatedAt(),
                    CustomerResponse.from(s.getCustomer()),
                    s.getWeightKg(),
                    s.getVolumeM3());
        }
    }

    public record DeliveryAttemptResponse(
            UUID id,
            UUID shipmentId,
            UUID customerId,
            Instant attemptedAt,
            String outcome,
            String rejectionReason
    ) {
        public static DeliveryAttemptResponse from(DeliveryAttempt a) {
            return new DeliveryAttemptResponse(a.getId(), a.getShipment().getId(), a.getCustomer().getId(),
                    a.getAttemptedAt(), name(a.getOutcome()), a.getRejectionReason());
        }
    }

    @Schema(description = "Una página de resultados")
    public record PageResponse<T>(
            @Schema(description = "Elementos de esta página") List<T> content,
            @Schema(description = "Número de página, desde 0", example = "0") int page,
            @Schema(description = "Tamaño de página pedido", example = "20") int size,
            @Schema(description = "Total de elementos que cumplen los filtros", example = "57") long totalElements,
            @Schema(description = "Total de páginas", example = "3") int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }

    // -----------------------------------------------------------------
    // Vehículos (HU-29)
    // -----------------------------------------------------------------

    @Schema(description = "Camión de la distribuidora (HU-29)")
    public record VehicleRequest(
            @NotBlank @Size(max = 12) @Pattern(regexp = "^[A-Za-z0-9 -]{4,12}$", message = "patente inválida (letras, números y guion)")
            @Schema(description = "Patente; se guarda en mayúsculas y sin espacios", example = "KXPT-21")
            String plate,
            @Size(max = 80) @Schema(description = "Nombre corto para la pantalla", example = "Camión 3/4")
            String name,
            @NotNull @Positive @Schema(description = "Capacidad de carga en kg", example = "3500")
            Double capacityKg,
            @NotNull @Positive @Schema(description = "Capacidad de carga en m³", example = "18")
            Double capacityM3,
            @NotNull @Schema(description = "Inicio de la jornada", type = "string", example = "08:00")
            LocalTime shiftStart,
            @NotNull @Schema(description = "Término de la jornada; debe ser posterior al inicio", type = "string", example = "20:00")
            LocalTime shiftEnd,
            @Schema(description = "false lo da de baja: no se usa en rutas nuevas, pero no se borra. Por defecto true")
            Boolean active
    ) {}

    @Schema(description = "Camión de la distribuidora (HU-29)")
    public record VehicleResponse(
            UUID id,
            @Schema(example = "KXPT-21") String plate,
            @Schema(example = "Camión 3/4") String name,
            @Schema(example = "3500") double capacityKg,
            @Schema(example = "18") double capacityM3,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime shiftStart,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "20:00") LocalTime shiftEnd,
            boolean active
    ) {
        public static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getPlate(), v.getName(), v.getCapacityKg(), v.getCapacityM3(),
                    v.getShiftStart(), v.getShiftEnd(), v.isActive());
        }
    }
}
