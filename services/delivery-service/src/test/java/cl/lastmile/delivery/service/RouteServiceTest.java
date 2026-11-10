package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService;
import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-30: el optimizador recibe una parada por local y la posición se guarda en todos sus envíos. */
class RouteServiceTest {

    private static final LocalDate DAY = LocalDate.of(2027, 1, 4);

    private final ShipmentRepository repository = mock(ShipmentRepository.class);
    private final RouteOptimizationService optimizer = mock(RouteOptimizationService.class);
    private final RouteService service = new RouteService(repository, optimizer);

    private static Shipment envio(String tracking, Customer local, int packages) {
        return Shipment.builder().id(UUID.randomUUID()).trackingCode(tracking).customer(local)
                .scheduledDate(DAY).packageCount(packages).build();
    }

    private static RouteStop routeStop(Shipment s, int position) {
        return new RouteStop(s.getId(), s.getCustomer().getFullName(), "Dirección", position,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1.0, List.of(), "DELIVERY", s.getPackageCount(),
                "AUTO", "Default", -33.44, -70.63);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordenaParadasYGuardaLaPosicionEnCadaEnvio() {
        Customer a = Customer.builder().id(UUID.randomUUID()).fullName("A").build();
        Customer b = Customer.builder().id(UUID.randomUUID()).fullName("B").build();
        Shipment a1 = envio("T-01", a, 1);
        Shipment b1 = envio("T-02", b, 2);
        Shipment a2 = envio("T-03", a, 3);
        when(repository.findByScheduledDate(DAY)).thenReturn(List.of(a1, b1, a2));
        // El optimizador decide B primero y A después.
        when(optimizer.buildRoute(org.mockito.ArgumentMatchers.anyList(), anyDouble(), anyDouble()))
                .thenReturn(List.of(routeStop(b1, 1), routeStop(a1, 2)));

        ConsolidatedRoute route = service.buildRouteForDate(DAY, -33.44, -70.62);

        ArgumentCaptor<List<Shipment>> sent = ArgumentCaptor.forClass(List.class);
        verify(optimizer).buildRoute(sent.capture(), anyDouble(), anyDouble());
        assertThat(sent.getValue()).containsExactly(a1, b1);         // un representante por local

        assertThat(route.stops()).extracting(RouteStop::shipmentId).containsExactly(b1.getId(), a1.getId());
        assertThat(route.stops().get(1).packageCount()).isEqualTo(4);  // 1 + 3
        assertThat(route.shipmentIds(route.stops().get(1))).containsExactly(a1.getId(), a2.getId());
        assertThat(route.shipmentIds(route.stops().get(0))).containsExactly(b1.getId());

        assertThat(a1.getRoutePosition()).isEqualTo(2);
        assertThat(a2.getRoutePosition()).isEqualTo(2);
        assertThat(b1.getRoutePosition()).isEqualTo(1);
        assertThat(List.of(a1, a2, b1)).allMatch(s -> s.getStatus() == Shipment.ShipmentStatus.ROUTED);
        verify(repository).saveAll(List.of(a1, b1, a2));
    }
}
