package cl.lastmile.delivery.service;

import cl.lastmile.delivery.dto.DeliveryDtos.RouteStateResponse;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import cl.lastmile.delivery.model.Route;
import cl.lastmile.delivery.model.Route.Status;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.repository.RouteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HU-17: estados de la ruta y bloqueo de una ruta confirmada. */
class RouteStateServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    private final RouteRepository repository = mock(RouteRepository.class);
    private final RouteStateService service = new RouteStateService(repository);

    private Route route(Status status) {
        Route r = Route.builder().id(UUID.randomUUID()).date(DAY).status(status).build();
        when(repository.findByDate(DAY)).thenReturn(Optional.of(r));
        when(repository.save(any(Route.class))).thenAnswer(i -> i.getArgument(0));
        return r;
    }

    @Test
    void confirmarExigeConductorYLoGuarda() {
        route(Status.DRAFT);
        UUID driver = UUID.randomUUID();

        assertThatThrownBy(() -> service.changeStatus(DAY, Status.CONFIRMED, null, null)).isInstanceOf(BadRequestException.class);
        RouteStateResponse confirmed = service.changeStatus(DAY, Status.CONFIRMED, driver, "  Pedro Conductor ");

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.editable()).isFalse();
        assertThat(confirmed.driverId()).isEqualTo(driver);
        assertThat(confirmed.driverName()).isEqualTo("Pedro Conductor");
        assertThat(confirmed.confirmedAt()).isNotNull();
    }

    @Test
    void volverABorradorLiberaAlConductor() {
        Route r = route(Status.CONFIRMED);
        r.setDriverId(UUID.randomUUID());
        r.setDriverName("Pedro");

        RouteStateResponse draft = service.changeStatus(DAY, Status.DRAFT, null, null);

        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(draft.editable()).isTrue();
        assertThat(draft.driverId()).isNull();
        assertThat(draft.confirmedAt()).isNull();
    }

    @Test
    void reconfirmarCambiaElConductor() {
        Route r = route(Status.CONFIRMED);
        r.setDriverId(UUID.randomUUID());
        UUID other = UUID.randomUUID();

        assertThat(service.changeStatus(DAY, Status.CONFIRMED, other, "Otro").driverId()).isEqualTo(other);
    }

    @Test
    void soloLosCambiosPermitidos() {
        route(Status.DRAFT);
        assertThatThrownBy(() -> service.changeStatus(DAY, Status.IN_PROGRESS, null, null)).isInstanceOf(ConflictException.class);
        route(Status.IN_PROGRESS);
        assertThatThrownBy(() -> service.changeStatus(DAY, Status.DRAFT, null, null)).isInstanceOf(ConflictException.class);
        assertThat(service.changeStatus(DAY, Status.CLOSED, null, null).status()).isEqualTo("CLOSED");
        route(Status.CLOSED);
        assertThatThrownBy(() -> service.changeStatus(DAY, Status.CONFIRMED, UUID.randomUUID(), "x")).isInstanceOf(ConflictException.class);
    }

    @Test
    void sinRutaArmadaEs404() {
        when(repository.findByDate(DAY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(DAY, Status.CONFIRMED, UUID.randomUUID(), "x"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatCode(() -> service.checkEditable(DAY)).doesNotThrowAnyException();
    }

    @Test
    void unaRutaConfirmadaNoSeRearma() {
        route(Status.CONFIRMED);
        assertThatThrownBy(() -> service.checkEditable(DAY)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("confirmada");
        assertThatThrownBy(() -> service.recordBuild(DAY, "VRPTW", null, null, List.of())).isInstanceOf(ConflictException.class);
    }

    @Test
    void elArmadoGuardaOptimizadorYEnlazaLosEnvios() {
        Route r = route(Status.DRAFT);
        Shipment s = Shipment.builder().id(UUID.randomUUID()).build();

        service.recordBuild(DAY, "VRPTW", "nota", null, List.of(s));

        assertThat(r.getOptimizer()).isEqualTo("VRPTW");
        assertThat(r.getOptimizerNote()).isEqualTo("nota");
        assertThat(s.getRouteId()).isEqualTo(r.getId());
    }
}
