package cl.lastmile.delivery;

import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.CustomerRepository;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.repository.ShipmentRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-02: el esquema se crea solo con Flyway sobre una base vacía y Hibernate
 * (ddl-auto: validate) lo acepta; además las entidades se pueden persistir y
 * leer de vuelta con las FKs y CHECKs del baseline.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationIT {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRepository customers;
    @Autowired ShipmentRepository shipments;
    @Autowired DeliveryAttemptRepository attempts;

    @Test
    void aplicaLasMigracionesYHibernateValidaElEsquema() {
        // V1 baseline (HU-02) + V2 modulación y tipo de local (HU-25).
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void persisteClienteEnvioEIntentoRespetandoLasFks() {
        Customer customer = customers.save(Customer.builder()
                .fullName("Cliente Prueba").address("Av. Providencia 1234").commune("Providencia")
                .latitude(-33.4263).longitude(-70.6170).externalCode("TEST-1").build());
        Shipment shipment = shipments.save(Shipment.builder()
                .trackingCode("TRK-SCHEMA-1").customer(customer).scheduledDate(LocalDate.now()).build());
        attempts.save(DeliveryAttempt.builder()
                .shipment(shipment).customer(customer).attemptedAt(Instant.now())
                .outcome(DeliveryAttempt.AttemptOutcome.ACCEPTED).build());

        assertThat(attempts.findByCustomerId(customer.getId())).hasSize(1);
        assertThat(shipments.findByTrackingCode("TRK-SCHEMA-1")).isPresent();
    }

    @Test
    void checkConstraintRechazaEstadosDesconocidos() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO customers (id, full_name, address) VALUES ('00000000-0000-0000-0000-000000000001', 'X', 'Y');
                INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date, status)
                VALUES (gen_random_uuid(), 'TRK-BAD', '00000000-0000-0000-0000-000000000001', CURRENT_DATE, 'LOST');
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fkImpideEnviosConClienteInexistente() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO shipments (id, tracking_code, customer_id, scheduled_date)
                VALUES (gen_random_uuid(), 'TRK-ORPHAN', gen_random_uuid(), CURRENT_DATE)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
