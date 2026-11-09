package cl.lastmile.delivery.dto;

import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeclaredWindow;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
            String label
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
            @Min(1) @Max(120) Integer stopMinutes
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
            int dayOffset
    ) {
        public static PlannedStopResponse of(RouteOptimizationService.RouteStop s,
                                             LocalTime arrival, LocalTime serviceStart, LocalTime departure,
                                             int waitMinutes, Integer travelMinutes, Integer legDistanceMeters,
                                             boolean outsideWindow, List<double[]> path, String timingSource,
                                             int dayOffset) {
            return new PlannedStopResponse(s.shipmentId(), s.customerName(), s.address(), s.position(),
                    s.windowStart(), s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(),
                    s.packageCount(), s.orderPreference(), s.label(), s.latitude(), s.longitude(),
                    arrival, serviceStart, departure, waitMinutes, travelMinutes, legDistanceMeters,
                    outsideWindow, path, timingSource, dayOffset);
        }
    }

    /** Edición parcial de una parada — refleja el panel de detalle de la app de referencia. */
    public record ShipmentUpdateRequest(
            String stopType,
            Integer packageCount,
            String orderPreference,
            String label,
            String notes
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
            String geocodeStatus
    ) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getAddress(),
                    c.getCommune(), c.getLatitude(), c.getLongitude(), c.getExternalCode(),
                    name(c.getBusinessType()), DeclaredWindowResponse.from(c.declaredWindow()),
                    name(c.getGeocodeStatus()));
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
            CustomerResponse customer
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
                    CustomerResponse.from(s.getCustomer()));
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

    private static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
