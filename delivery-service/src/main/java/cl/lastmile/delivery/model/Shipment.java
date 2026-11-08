package cl.lastmile.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue
    private UUID id;

    /** Código de tracking público que ve el cliente final. */
    @Column(nullable = false, unique = true)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ShipmentStatus status = ShipmentStatus.PENDING;

    /** Origen de la carga (importación masiva, integración e-commerce, manual). */
    private String source;

    private String notes;

    /** Posición sugerida dentro de la ruta del camión (1 = primero a entregar). */
    private Integer routePosition;

    /** Entrega o recogida — igual que el selector "Tipo" de la app de referencia. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StopType stopType = StopType.DELIVERY;

    @Builder.Default
    private Integer packageCount = 1;

    /**
     * Cómo debe tratar el optimizador esta parada dentro de la ruta:
     * FIRST la fuerza al inicio, LAST al final, AUTO deja que el algoritmo
     * decida en base a ventana horaria aprendida + cercanía — igual que el
     * selector "Orden: Primera / Automático / Última" de la app de referencia.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderPreference orderPreference = OrderPreference.AUTO;

    /** Etiqueta de color libre (p.ej. "Default", "Afternoon Pickup") para agrupar visualmente en el mapa. */
    private String label;

    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum ShipmentStatus {
        PENDING, ROUTED, OUT_FOR_DELIVERY, DELIVERED, REJECTED, RESCHEDULED
    }

    public enum StopType {
        DELIVERY, PICKUP
    }

    public enum OrderPreference {
        FIRST, AUTO, LAST
    }
}
