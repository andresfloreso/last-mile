package cl.lastmile.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public class DeliveryDtos {

    public record CustomerRequest(
            @NotBlank String fullName,
            String phone,
            @NotBlank String address,
            String commune,
            Double latitude,
            Double longitude,
            String externalCode
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
}
