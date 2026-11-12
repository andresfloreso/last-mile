package cl.lastmile.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La ruta de un día (HU-17): su estado, con qué optimizador y camión se armó y a qué conductor se asignó.
 * El orden de las paradas vive en {@link Shipment#getRoutePosition()}; {@link Shipment#getRouteId()} dice
 * a qué ruta pertenece cada envío.
 */
@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {

    /**
     * DRAFT: se puede rearmar y reordenar. CONFIRMED: asignada a un conductor; ya no se reordena.
     * IN_PROGRESS: el conductor salió a repartir. CLOSED: terminó el día.
     */
    public enum Status { DRAFT, CONFIRMED, IN_PROGRESS, CLOSED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "route_date", nullable = false, unique = true)
    private LocalDate date;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    /** VRPTW o NEAREST_NEIGHBOR (HU-28); null si la ruta viene de antes de V6. */
    @Column(length = 20)
    private String optimizer;

    @Column(name = "optimizer_note", columnDefinition = "TEXT")
    private String optimizerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    /** Id del usuario conductor en auth-service (otra base: sin FK). */
    @Column(name = "driver_id")
    private UUID driverId;

    /** Nombre del conductor al asignarlo, para mostrarlo sin consultar auth-service. */
    @Column(name = "driver_name", length = 120)
    private String driverName;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Solo un borrador se rearma o se reordena a mano. */
    public boolean isEditable() {
        return status == Status.DRAFT;
    }
}
