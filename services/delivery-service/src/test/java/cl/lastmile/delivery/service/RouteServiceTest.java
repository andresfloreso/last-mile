package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.OptimizedRoute;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.RouteProblem;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Vehicle;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.repository.VehicleRepository;
import cl.lastmile.delivery.service.RouteService.ConsolidatedRoute;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-30: el optimizador recibe una parada por local y la posición se guarda en todos sus envíos.
 * HU-28: recibe también la carga de cada parada, el camión, la hora de salida y la duración de parada.
 */
class RouteServiceTest {

    private static final LocalDate DAY = LocalDate.of(2027, 1, 4);
    private static final LocalTime EIGHT = LocalTime.of(8, 0);

    private final ShipmentRepository repository = mock(ShipmentRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final RouteOptimizer optimizer = mock(RouteOptimizer.class);
    private final RouteService service = new RouteService(repository, vehicles, optimizer);

    private Shipment a1;
    private Shipment b1;
    private Shipment a2;

    private static Shipment envio(String tracking, Customer local, int packages, double kg) {
        return Shipment.builder().id(UUID.randomUUID()).trackingCode(tracking).customer(local)
                .scheduledDate(DAY).packageCount(packages).weightKg(kg).volumeM3(0.05).build();
    }

    private static RouteStop routeStop(Shipment s, int position) {
        return new RouteStop(s.getId(), s.getCustomer().getFullName(), "Dirección", position,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1.0, List.of(), "DELIVERY", s.getPackageCount(),
                "AUTO", "Default", -33.44, -70.63);
    }

    private static Vehicle truck(String plate, double kg, boolean active) {
        return Vehicle.builder().id(UUID.randomUUID()).plate(plate).capacityKg(kg).capacityM3(10.0)
                .shiftStart(EIGHT).shiftEnd(LocalTime.of(21, 0)).active(active).build();
    }

    @BeforeEach
    void dia() {
        Customer a = Customer.builder().id(UUID.randomUUID()).fullName("A").build();
        Customer b = Customer.builder().id(UUID.randomUUID()).fullName("B").build();
        a1 = envio("T-01", a, 1, 10);
        b1 = envio("T-02", b, 2, 20);
        a2 = envio("T-03", a, 3, 30);
        when(repository.findByScheduledDateAndStatusIn(DAY, RouteService.ROUTABLE)).thenReturn(List.of(a1, b1, a2));
        // El optimizador decide B primero y A después.
        when(optimizer.optimize(any())).thenReturn(new OptimizedRoute(
                List.of(routeStop(b1, 1), routeStop(a1, 2)), RouteOptimizer.VRPTW, null));
    }

    @Test
    void soloPideLosEnviosPendientesOYaRuteados() {
        when(vehicles.findByActiveTrueOrderByPlateAsc()).thenReturn(List.of());

        service.buildRouteForDate(DAY, -33.44, -70.62, EIGHT, 10, null);

        // #70: nunca lee (ni reescribe) los envíos cerrados de la fecha.
        verify(repository).findByScheduledDateAndStatusIn(DAY, RouteService.ROUTABLE);
        verify(repository, org.mockito.Mockito.never()).findByScheduledDate(any());
        assertThat(RouteService.ROUTABLE).containsExactlyInAnyOrder(
                Shipment.ShipmentStatus.PENDING, Shipment.ShipmentStatus.ROUTED);
    }

    @Test
    void ordenaParadasYGuardaLaPosicionEnCadaEnvio() {
        when(vehicles.findByActiveTrueOrderByPlateAsc()).thenReturn(List.of());

        ConsolidatedRoute route = service.buildRouteForDate(DAY, -33.44, -70.62, EIGHT, 10, null);

        ArgumentCaptor<RouteProblem> sent = ArgumentCaptor.forClass(RouteProblem.class);
        verify(optimizer).optimize(sent.capture());
        RouteProblem problem = sent.getValue();
        assertThat(problem.shipments()).containsExactly(a1, b1);          // un representante por local
        assertThat(problem.stops().get(0).weightKg()).isEqualTo(40.0);    // parada A: 10 + 30 kg
        assertThat(problem.departure()).isEqualTo(EIGHT);
        assertThat(problem.stopMinutes()).isEqualTo(10);
        assertThat(problem.vehicle()).isNull();                           // sin camiones: sin límites

        assertThat(route.stops()).extracting(RouteStop::shipmentId).containsExactly(b1.getId(), a1.getId());
        assertThat(route.stops().get(1).packageCount()).isEqualTo(4);    // 1 + 3
        assertThat(route.shipmentIds(route.stops().get(1))).containsExactly(a1.getId(), a2.getId());
        assertThat(route.shipmentIds(route.stops().get(0))).containsExactly(b1.getId());
        // HU-29: peso y volumen de la parada A = suma de a1 y a2.
        assertThat(route.load(route.stops().get(1)).weightKg()).isEqualTo(40.0);
        assertThat(route.load(route.stops().get(1)).volumeM3()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(route.optimizer()).isEqualTo(RouteOptimizer.VRPTW);
        assertThat(route.vehicle()).isNull();

        assertThat(a1.getRoutePosition()).isEqualTo(2);
        assertThat(a2.getRoutePosition()).isEqualTo(2);
        assertThat(b1.getRoutePosition()).isEqualTo(1);
        assertThat(List.of(a1, a2, b1)).allMatch(s -> s.getStatus() == Shipment.ShipmentStatus.ROUTED);
        verify(repository).saveAll(List.of(a1, b1, a2));
    }

    @Test
    void sinCamionElegidoUsaElActivoDeMayorCapacidad() {
        when(vehicles.findByActiveTrueOrderByPlateAsc())
                .thenReturn(List.of(truck("FURGON", 1200, true), truck("TRES-CUARTOS", 3500, true)));

        ConsolidatedRoute route = service.buildRouteForDate(DAY, -33.44, -70.62, EIGHT, 10, " ");

        ArgumentCaptor<RouteProblem> sent = ArgumentCaptor.forClass(RouteProblem.class);
        verify(optimizer).optimize(sent.capture());
        assertThat(sent.getValue().vehicle()).isEqualTo(
                new RouteOptimizer.Vehicle("TRES-CUARTOS", 3500, 10, LocalTime.of(21, 0)));
        assertThat(route.vehicle()).isEqualTo("TRES-CUARTOS");
    }

    @Test
    void elCamionPedidoDebeExistirYEstarActivo() {
        Vehicle furgon = truck("FURGON", 1200, true);
        Vehicle baja = truck("BAJA", 1200, false);
        when(vehicles.findById(furgon.getId())).thenReturn(Optional.of(furgon));
        when(vehicles.findById(baja.getId())).thenReturn(Optional.of(baja));

        assertThat(service.buildRouteForDate(DAY, -33.44, -70.62, EIGHT, 10, furgon.getId().toString()).vehicle())
                .isEqualTo("FURGON");
        assertThatThrownBy(() -> service.buildRouteForDate(DAY, -33.44, -70.62, EIGHT, 10, baja.getId().toString()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("dado de baja");
        assertThatThrownBy(() -> service.buildRouteForDate(DAY, -33.44, -70.62, EIGHT, 10, UUID.randomUUID().toString()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
