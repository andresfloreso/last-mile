package cl.lastmile.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    /** Hash bcrypt de la contraseña — nunca se guarda en texto plano. */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.OPERATOR;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum Role {
        ADMIN,      // dueños de la plataforma logística
        DISPATCHER, // arma/gestiona rutas
        DRIVER,     // conductor / repartidor
        OPERATOR,   // carga documentos, atiende clientes
        CLIENT      // cliente final que hace seguimiento
    }
}
