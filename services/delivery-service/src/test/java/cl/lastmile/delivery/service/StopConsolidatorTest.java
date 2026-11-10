package cl.lastmile.delivery.service;

import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Shipment.OrderPreference;
import cl.lastmile.delivery.service.StopConsolidator.DeliveryStop;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-30: una parada por local, con la suma de bultos y la preferencia de orden de sus envíos. */
class StopConsolidatorTest {

    private static final LocalDate DAY = LocalDate.of(2027, 1, 4);

    private static Customer local(String name) {
        return Customer.builder().id(UUID.randomUUID()).fullName(name).build();
    }

    private static Shipment envio(String tracking, Customer local, int packages, OrderPreference order) {
        return Shipment.builder().id(UUID.randomUUID()).trackingCode(tracking).customer(local)
                .scheduledDate(DAY).packageCount(packages).orderPreference(order)
                .weightKg(10.0 * packages).volumeM3(0.02 * packages).build();
    }

    @Test
    void agrupaLosEnviosDelMismoLocalEnUnaParadaYSumaLosBultos() {
        Customer a = local("A");
        Customer b = local("B");
        Shipment a2 = envio("T-03", a, 2, OrderPreference.AUTO);
        Shipment b1 = envio("T-02", b, 1, OrderPreference.AUTO);
        Shipment a1 = envio("T-01", a, 3, OrderPreference.AUTO);

        List<DeliveryStop> stops = StopConsolidator.consolidate(List.of(a2, b1, a1));

        assertThat(stops).hasSize(2);
        DeliveryStop stopA = stops.get(0);
        assertThat(stopA.customerId()).isEqualTo(a.getId());
        assertThat(stopA.representative()).isSameAs(a1);           // tracking menor
        assertThat(stopA.shipmentIds()).containsExactly(a1.getId(), a2.getId());
        assertThat(stopA.packageCount()).isEqualTo(5);
        // HU-29: la parada suma el peso y el volumen de sus envíos.
        assertThat(stopA.weightKg()).isEqualTo(50.0);
        assertThat(stopA.volumeM3()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(stopA.load().shipmentIds()).containsExactly(a1.getId(), a2.getId());
        assertThat(stopA.load().weightKg()).isEqualTo(50.0);
        assertThat(stops.get(1).shipmentIds()).containsExactly(b1.getId());
        assertThat(stops.get(1).packageCount()).isEqualTo(1);
    }

    @Test
    void laParadaVaPrimeraSiAlgunEnvioLoPideAunqueOtroPidaUltima() {
        Customer a = local("A");
        Shipment last = envio("T-01", a, 1, OrderPreference.LAST);
        Shipment auto = envio("T-02", a, 1, OrderPreference.AUTO);
        Shipment first = envio("T-03", a, 1, OrderPreference.FIRST);

        DeliveryStop stop = StopConsolidator.consolidate(List.of(last, auto, first)).get(0);

        assertThat(stop.representative()).isSameAs(first);
        assertThat(stop.shipmentIds()).containsExactly(first.getId(), last.getId(), auto.getId());
    }

    @Test
    void siNingunoPidePrimeraMandaLaUltima() {
        Customer a = local("A");
        Shipment auto = envio("T-01", a, 1, OrderPreference.AUTO);
        Shipment last = envio("T-02", a, 1, OrderPreference.LAST);

        assertThat(StopConsolidator.consolidate(List.of(auto, last)).get(0).representative()).isSameAs(last);
    }

    @Test
    void datosNulosCuentanComoCeroYSinEnviosNoHayParadas() {
        Customer a = local("A");
        Shipment s = envio("T-01", a, 1, OrderPreference.AUTO);
        s.setPackageCount(null);
        s.setWeightKg(null);
        s.setVolumeM3(null);

        DeliveryStop stop = StopConsolidator.consolidate(List.of(s)).get(0);
        assertThat(stop.packageCount()).isZero();
        assertThat(stop.weightKg()).isZero();
        assertThat(stop.volumeM3()).isZero();
        assertThat(StopConsolidator.StopLoad.single(s.getId()).weightKg()).isNull();
        assertThat(StopConsolidator.consolidate(List.of())).isEmpty();
    }
}
