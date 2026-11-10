package cl.lastmile.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** Camión de la distribuidora (HU-29): patente, capacidad en kg y m³, y jornada. */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue
    private UUID id;

    /** Patente, en mayúsculas y sin espacios (p. ej. "KXPT-21"). */
    @Column(nullable = false, unique = true, length = 12)
    private String plate;

    /** Nombre corto para la pantalla (p. ej. "Camión 3/4"). */
    @Column(length = 80)
    private String name;

    @Column(name = "capacity_kg", nullable = false)
    private Double capacityKg;

    @Column(name = "capacity_m3", nullable = false)
    private Double capacityM3;

    /** Jornada: hora de salida más temprana y hora a la que debe terminar. */
    @Column(nullable = false)
    private LocalTime shiftStart;

    @Column(nullable = false)
    private LocalTime shiftEnd;

    /** false: dado de baja (no se usa en rutas nuevas, pero no se borra). */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
