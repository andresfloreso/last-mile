package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.TimeWindowLearningService;
import cl.lastmile.delivery.algorithm.TimeWindowLearningService.CustomerTimeWindowProfile;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-32: la ruta guardada sale en el orden guardado, una parada por local, sin escribir nada. */
class SavedRouteServiceTest {

    private static final LocalDate DAY = LocalDate.of(2027, 1, 4);

    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final TimeWindowLearningService learning = mock(TimeWindowLearningService.class);
    private final DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
    private final SavedRouteService service = new SavedRouteService(shipments, learning, attempts);

    private static Customer local(String name) {
        return Customer.builder().id(UUID.randomUUID()).fullName(name).address("Dirección " + name)
                .latitude(-33.44).longitude(-70.63).build();
    }

    private static Shipment envio(Customer local, Integer position, int packages) {
        return Shipment.builder().id(UUID.randomUUID()).trackingCode(UUID.randomUUID().toString()).customer(local)
                .scheduledDate(DAY).routePosition(position).packageCount(packages).weightKg(10.0).volumeM3(0.05).build();
    }

    private void windows(int start, int end) {
        when(learning.learn(any(UUID.class), any(DeliveryAttemptRepository.class))).thenAnswer(inv ->
                new CustomerTimeWindowProfile(inv.getArgument(0), start, end, List.of(14), 5, 0.8, List.of(), 4, 5));
    }

    @Test
    void devuelveLasParadasEnElOrdenGuardadoConSuVentanaReal() {
        Customer a = local("A");
        Customer b = local("B");
        Customer c = local("C");
        when(shipments.findByScheduledDate(DAY)).thenReturn(List.of(envio(c, 3, 1), envio(a, 1, 2), envio(b, 2, 1)));
        windows(9, 11);

        ConsolidatedRoute route = service.savedRouteForDate(DAY);

        assertThat(route.stops()).extracting(RouteStop::customerName).containsExactly("A", "B", "C");
        assertThat(route.stops()).extracting(RouteStop::position).containsExactly(1, 2, 3);
        RouteStop first = route.stops().get(0);
        assertThat(first.windowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(first.windowEnd()).isEqualTo(LocalTime.of(11, 0));
        assertThat(first.confidence()).isEqualTo(0.8);
        assertThat(first.hoursToAvoid()).containsExactly(14);
        assertThat(first.packageCount()).isEqualTo(2);
        assertThat(route.optimizer()).isNull();
        verify(shipments, never()).saveAll(any());
    }

    @Test
    void losEnviosDelMismoLocalSonUnaParadaConSuCarga() {
        Customer a = local("A");
        Customer b = local("B");
        Shipment a1 = envio(a, 2, 1);
        Shipment a2 = envio(a, 2, 3);
        when(shipments.findByScheduledDate(DAY)).thenReturn(List.of(a1, envio(b, 1, 1), a2));
        windows(9, 18);

        ConsolidatedRoute route = service.savedRouteForDate(DAY);

        assertThat(route.stops()).extracting(RouteStop::customerName).containsExactly("B", "A");
        RouteStop stopA = route.stops().get(1);
        assertThat(stopA.packageCount()).isEqualTo(4);
        assertThat(route.shipmentIds(stopA)).containsExactlyInAnyOrder(a1.getId(), a2.getId());
        assertThat(route.load(stopA).weightKg()).isEqualTo(20.0);
    }

    @Test
    void unLocalSinPosicionQuedaFueraYUnoConAlgunEnvioPosicionadoEntra() {
        Customer a = local("A");
        Customer nuevo = local("Nuevo");
        Customer mixto = local("Mixto");
        // "Mixto" tiene un envío ruteado y otro importado después de armar la ruta.
        when(shipments.findByScheduledDate(DAY)).thenReturn(List.of(
                envio(a, 1, 1), envio(nuevo, null, 1), envio(mixto, null, 1), envio(mixto, 2, 1)));
        windows(9, 18);

        ConsolidatedRoute route = service.savedRouteForDate(DAY);

        assertThat(route.stops()).extracting(RouteStop::customerName).containsExactly("A", "Mixto");
        assertThat(route.stops().get(1).position()).isEqualTo(2);
    }

    @Test
    void sinRutaArmadaNoHayParadas() {
        Customer a = local("A");
        when(shipments.findByScheduledDate(DAY)).thenReturn(List.of(envio(a, null, 1)));

        assertThat(service.savedRouteForDate(DAY).stops()).isEmpty();
    }
}
