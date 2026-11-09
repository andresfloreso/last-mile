package cl.lastmile.delivery.dto;

import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeclaredWindow;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
            double depotLng
    ) {}

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
            DeclaredWindowResponse declaredWindow
    ) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getAddress(),
                    c.getCommune(), c.getLatitude(), c.getLongitude(), c.getExternalCode(),
                    name(c.getBusinessType()), DeclaredWindowResponse.from(c.declaredWindow()));
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
