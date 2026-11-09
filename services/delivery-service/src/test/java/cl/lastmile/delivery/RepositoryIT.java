package cl.lastmile.delivery;

import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.CustomerRepository;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-04: repositorios contra PostgreSQL real (Testcontainers), con el esquema
 * de Flyway. Cada test corre en una transacción que se revierte al terminar.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RepositoryIT {

    @Autowired CustomerRepository customers;
    @Autowired ShipmentRepository shipments;
    @Autowired DeliveryAttemptRepository attempts;

    private static final LocalDate DAY = LocalDate.of(2031, 3, 14); // fecha sin datos de otros tests

    private Customer customer(String externalCode) {
        return customers.save(Customer.builder()
                .fullName("Minimarket " + externalCode).address("Calle 1").commune("Ñuñoa")
                .latitude(-33.45).longitude(-70.6).externalCode(externalCode).build());
    }

    private Shipment shipment(Customer customer, String tracking, Integer routePosition) {
        return shipments.save(Shipment.builder()
                .trackingCode(tracking).customer(customer).scheduledDate(DAY).routePosition(routePosition).build());
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void buscaUnLocalPorSuCodigoExterno() {
        String code = unique("EXT");
        Customer saved = customer(code);

        assertThat(customers.findByExternalCode(code)).get().extracting(Customer::getId).isEqualTo(saved.getId());
        assertThat(customers.findByExternalCode(unique("NO"))).isEmpty();
    }

    @Test
    void buscaEnviosPorTrackingYDetectaDuplicados() {
        String tracking = unique("TRK");
        shipment(customer(unique("EXT")), tracking, null);

        assertThat(shipments.findByTrackingCode(tracking)).isPresent();
        assertThat(shipments.existsByTrackingCode(tracking)).isTrue();
        assertThat(shipments.existsByTrackingCode(unique("TRK"))).isFalse();
    }

    @Test
    void laBaseRechazaUnTrackingRepetido() {
        Customer c = customer(unique("EXT"));
        String tracking = unique("TRK");
        shipment(c, tracking, null);

        assertThatThrownBy(() -> {
            shipments.save(Shipment.builder().trackingCode(tracking).customer(c).scheduledDate(DAY).build());
            shipments.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listaLosEnviosDeUnaFechaYLosOrdenaPorPosicionEnLaRuta() {
        Customer c = customer(unique("EXT"));
        shipment(c, unique("C"), 3);
        shipment(c, unique("A"), 1);
        shipment(c, unique("B"), 2);
        shipments.save(Shipment.builder().trackingCode(unique("OTRO")).customer(c)
                .scheduledDate(DAY.plusDays(1)).build());

        assertThat(shipments.findByScheduledDate(DAY)).hasSize(3);
        assertThat(shipments.findByScheduledDateOrderByRoutePositionAsc(DAY))
                .extracting(Shipment::getRoutePosition).containsExactly(1, 2, 3);
    }

    @Test
    void devuelveLosIntentosDeEntregaDeUnLocal() {
        Customer c = customer(unique("EXT"));
        Customer other = customer(unique("EXT"));
        Shipment s = shipment(c, unique("TRK"), null);
        Shipment s2 = shipment(other, unique("TRK"), null);
        attempts.save(DeliveryAttempt.builder().shipment(s).customer(c)
                .attemptedAt(Instant.parse("2031-03-14T15:00:00Z")).outcome(AttemptOutcome.ACCEPTED).build());
        attempts.save(DeliveryAttempt.builder().shipment(s).customer(c)
                .attemptedAt(Instant.parse("2031-03-13T13:00:00Z")).outcome(AttemptOutcome.REJECTED_NOT_HOME)
                .rejectionReason("Cerrado").build());
        attempts.save(DeliveryAttempt.builder().shipment(s2).customer(other)
                .attemptedAt(Instant.parse("2031-03-14T15:00:00Z")).outcome(AttemptOutcome.ACCEPTED).build());

        assertThat(attempts.findByCustomerId(c.getId()))
                .extracting(DeliveryAttempt::getOutcome)
                .containsExactlyInAnyOrder(AttemptOutcome.ACCEPTED, AttemptOutcome.REJECTED_NOT_HOME);
        assertThat(attempts.findByCustomerId(UUID.randomUUID())).isEmpty();
    }
}
