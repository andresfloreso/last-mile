package cl.lastmile.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Cada vez que el camión llega donde un cliente, se registra un intento con
 * la hora exacta y si el cliente recibió o rechazó. El historial de estos
 * intentos, agrupado por cliente, es lo que TimeWindowLearningService usa
 * para inferir en qué ventana horaria conviene volver a ir.
 */
@Entity
@Table(name = "delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAttempt {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptOutcome outcome;

    private String rejectionReason;

    public enum AttemptOutcome {
        ACCEPTED, REJECTED_NOT_HOME, REJECTED_REFUSED, RESCHEDULED_BY_CUSTOMER
    }
}
