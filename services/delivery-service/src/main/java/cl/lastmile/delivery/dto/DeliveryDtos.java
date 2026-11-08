package cl.lastmile.delivery.dto;

import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
            String externalCode
    ) {
        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getAddress(),
                    c.getCommune(), c.getLatitude(), c.getLongitude(), c.getExternalCode());
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
