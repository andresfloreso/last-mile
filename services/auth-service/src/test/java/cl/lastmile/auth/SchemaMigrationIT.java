package cl.lastmile.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-02: el esquema se crea solo con Flyway sobre una base vacía y Hibernate
 * (ddl-auto: validate) lo acepta. Si una entidad y la migración divergen, el
 * contexto no levanta y este test falla.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaBaselineYHibernateValidaElEsquema() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void checkConstraintRechazaRolesDesconocidos() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (id, full_name, email, password_hash, role)
                VALUES (gen_random_uuid(), 'X', 'x@lastmile.cl', 'hash', 'SUPERUSER')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
