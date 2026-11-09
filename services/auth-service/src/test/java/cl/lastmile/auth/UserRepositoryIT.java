package cl.lastmile.auth;

import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-04: UserRepository contra PostgreSQL real (Testcontainers) con el esquema
 * de Flyway. Cada test se revierte al terminar.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryIT {

    @Autowired UserRepository users;

    private static String email() {
        return "repo-" + UUID.randomUUID().toString().substring(0, 8) + "@lastmile.cl";
    }

    private User save(String email, User.Role role) {
        return users.saveAndFlush(User.builder()
                .fullName("Usuario de prueba").email(email).passwordHash("$2a$12$hash").role(role).build());
    }

    @Test
    void buscaPorEmailYSabeSiExiste() {
        String email = email();
        User saved = save(email, User.Role.DISPATCHER);

        assertThat(users.findByEmail(email)).get().extracting(User::getId).isEqualTo(saved.getId());
        assertThat(users.existsByEmail(email)).isTrue();
        assertThat(users.existsByEmail(email())).isFalse();
        assertThat(users.findByEmail(email())).isEmpty();
    }

    @Test
    void guardaRolYValoresPorDefecto() {
        User saved = save(email(), User.Role.DRIVER);

        User found = users.findById(saved.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(User.Role.DRIVER);
        assertThat(found.isEnabled()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(users.existsByRole(User.Role.DRIVER)).isTrue();
    }

    @Test
    void laBaseRechazaUnEmailRepetido() {
        String email = email();
        save(email, User.Role.OPERATOR);

        assertThatThrownBy(() -> save(email, User.Role.CLIENT)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
