package cl.lastmile.delivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    private String phone;

    @Column(nullable = false)
    private String address;

    private String commune; // comuna (Chile)

    private Double latitude;
    private Double longitude;

    /** Identificador externo usado en las planillas/importaciones (RUT, código de cliente, etc). */
    private String externalCode;
}
