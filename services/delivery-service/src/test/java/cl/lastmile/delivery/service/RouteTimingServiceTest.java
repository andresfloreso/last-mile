package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.dto.DeliveryDtos.PlannedStopResponse;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.geo.OsrmRoutingClient.Leg;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HU-14: hora estimada por parada sobre el orden que entrega el optimizador. */
class RouteTimingServiceTest {

    private static final double DEPOT_LAT = -33.45;
    private static final double DEPOT_LNG = -70.66;

    private final OsrmRoutingClient osrm = mock(OsrmRoutingClient.class);
    private final RouteTimingService timing = new RouteTimingService(osrm, 25);

    private static RouteStop stop(int position, String name, int windowStart, int windowEnd, String order, Double lat, Double lng) {
        return new RouteStop(UUID.randomUUID(), name, "Dirección " + name, position,
                LocalTime.of(windowStart, 0), LocalTime.of(windowEnd, 0), 1.0, List.of(),
                "DELIVERY", 1, order, "Default", lat, lng);
    }

    private static Leg leg(int minutes, int meters) {
        return new Leg(minutes * 60.0, meters, List.of(new double[]{0, 0}, new double[]{1, 1}));
    }

    @Test
    void sumaViajeYDuracionDeParadaYEsperaSiLlegaAntesDeLaVentana() {
        List<RouteStop> route = List.of(
                stop(1, "A", 8, 10, "AUTO", -33.40, -70.60),
                stop(2, "B", 9, 11, "AUTO", -33.41, -70.61),
                stop(3, "C", 12, 14, "AUTO", -33.42, -70.62));
        when(osrm.route(anyList())).thenReturn(Optional.of(List.of(leg(20, 8000), leg(15, 5000), leg(30, 12000))));

        List<PlannedStopResponse> plan = timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10);

        // A: sale 08:00 + 20 min = 08:20, dentro de 08–10; sale 08:30.
        assertThat(plan.get(0).estimatedArrival()).isEqualTo(LocalTime.of(8, 20));
        assertThat(plan.get(0).serviceStart()).isEqualTo(LocalTime.of(8, 20));
        assertThat(plan.get(0).estimatedDeparture()).isEqualTo(LocalTime.of(8, 30));
        assertThat(plan.get(0).waitMinutes()).isZero();
        assertThat(plan.get(0).travelMinutes()).isEqualTo(20);
        assertThat(plan.get(0).legDistanceMeters()).isEqualTo(8000);
        // B: 08:30 + 15 = 08:45, antes de que abra (09:00): espera 15 min.
        assertThat(plan.get(1).estimatedArrival()).isEqualTo(LocalTime.of(8, 45));
        assertThat(plan.get(1).serviceStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(plan.get(1).waitMinutes()).isEqualTo(15);
        assertThat(plan.get(1).estimatedDeparture()).isEqualTo(LocalTime.of(9, 10));
        // C: 09:10 + 30 = 09:40, espera hasta las 12:00.
        assertThat(plan.get(2).serviceStart()).isEqualTo(LocalTime.of(12, 0));
        assertThat(plan.get(2).waitMinutes()).isEqualTo(140);
        assertThat(plan).noneMatch(PlannedStopResponse::outsideWindow);
        assertThat(plan).allMatch(p -> p.timingSource().equals("OSRM"));
    }

    @Test
    void marcaFueraDeVentanaSiLaEntregaEmpiezaCuandoYaCerro() {
        List<RouteStop> route = List.of(
                stop(1, "Lejos", 8, 9, "AUTO", -33.40, -70.60),
                stop(2, "JustoAlCierre", 9, 10, "AUTO", -33.41, -70.61),
                stop(3, "Antes", 8, 12, "AUTO", -33.42, -70.62));
        // 08:00 + 70 = 09:10 (cerró 09:00) · 09:20 + 40 = 10:00 (cierra 10:00: ya no alcanza) · 10:10 + 5 = 10:15
        when(osrm.route(anyList())).thenReturn(Optional.of(List.of(leg(70, 1), leg(40, 1), leg(5, 1))));

        List<PlannedStopResponse> plan = timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10);

        assertThat(plan).extracting(PlannedStopResponse::outsideWindow).containsExactly(true, true, false);
        assertThat(plan.get(1).serviceStart()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void respetaElOrdenDelOptimizadorConLasParadasPrimeraYUltima() {
        List<RouteStop> route = List.of(
                stop(1, "Primera", 15, 17, "FIRST", -33.40, -70.60),
                stop(2, "Auto", 9, 11, "AUTO", -33.41, -70.61),
                stop(3, "Ultima", 9, 11, "LAST", -33.42, -70.62));
        when(osrm.route(anyList())).thenReturn(Optional.of(List.of(leg(10, 1), leg(10, 1), leg(10, 1))));

        List<PlannedStopResponse> plan = timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10);

        // No reordena: la "Primera" va primero aunque haga esperar hasta las 15:00, y las demás quedan tarde.
        assertThat(plan).extracting(PlannedStopResponse::customerName).containsExactly("Primera", "Auto", "Ultima");
        assertThat(plan).extracting(PlannedStopResponse::position).containsExactly(1, 2, 3);
        assertThat(plan).extracting(PlannedStopResponse::orderPreference).containsExactly("FIRST", "AUTO", "LAST");
        assertThat(plan.get(0).serviceStart()).isEqualTo(LocalTime.of(15, 0));
        assertThat(plan.get(1).outsideWindow()).isTrue();
        assertThat(plan.get(2).outsideWindow()).isTrue();
    }

    @Test
    void consultaOsrmConElDepositoYLasParadasEnElOrdenDeLaRuta() {
        List<RouteStop> route = List.of(
                stop(1, "A", 8, 18, "AUTO", -33.40, -70.60),
                stop(2, "B", 8, 18, "AUTO", -33.41, -70.61));
        when(osrm.route(anyList())).thenAnswer(inv -> {
            List<double[]> points = inv.getArgument(0);
            assertThat(points).hasSize(3);
            assertThat(points.get(0)).containsExactly(DEPOT_LAT, DEPOT_LNG);
            assertThat(points.get(1)).containsExactly(-33.40, -70.60);
            assertThat(points.get(2)).containsExactly(-33.41, -70.61);
            return Optional.of(List.of(leg(1, 1), leg(1, 1)));
        });

        timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10);
    }

    @Test
    void sinOsrmEstimaConLineaRectaAVelocidadUrbana() {
        // ~1,11 km al norte del depósito (0,01° de latitud).
        List<RouteStop> route = List.of(stop(1, "A", 8, 18, "AUTO", DEPOT_LAT + 0.01, DEPOT_LNG));
        when(osrm.route(anyList())).thenReturn(Optional.empty());

        PlannedStopResponse p = timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10).get(0);

        double meters = RouteTimingService.haversineMeters(DEPOT_LAT, DEPOT_LNG, DEPOT_LAT + 0.01, DEPOT_LNG) * 1.3;
        assertThat(p.legDistanceMeters()).isEqualTo((int) Math.round(meters));
        // 1445 m a 25 km/h ≈ 3,5 min
        assertThat(p.travelMinutes()).isCloseTo((int) Math.round(meters / (25_000.0 / 60)), within(1));
        assertThat(p.timingSource()).isEqualTo("ESTIMATED");
        // La polilínea une el depósito y el local en línea recta.
        assertThat(p.path()).hasSize(2);
        assertThat(p.path().get(0)).containsExactly(DEPOT_LAT, DEPOT_LNG);
    }

    @Test
    void unLocalSinCoordenadasNoTieneTramoNiCortaElRestoDeLaRuta() {
        List<RouteStop> route = List.of(
                stop(1, "A", 8, 18, "AUTO", -33.40, -70.60),
                stop(2, "SinUbicacion", 8, 18, "AUTO", null, null),
                stop(3, "C", 8, 18, "AUTO", -33.42, -70.62));
        // OSRM recibe solo depósito, A y C: 2 tramos.
        when(osrm.route(anyList())).thenAnswer(inv -> {
            assertThat((List<?>) inv.getArgument(0)).hasSize(3);
            return Optional.of(List.of(leg(10, 1000), leg(20, 2000)));
        });

        List<PlannedStopResponse> plan = timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10);

        assertThat(plan.get(1).timingSource()).isEqualTo("NO_LOCATION");
        assertThat(plan.get(1).travelMinutes()).isNull();
        assertThat(plan.get(1).path()).isEmpty();
        assertThat(plan.get(1).estimatedArrival()).isEqualTo(LocalTime.of(8, 20)); // en la salida de A
        // C usa el segundo tramo de OSRM: 08:30 + 20.
        assertThat(plan.get(2).estimatedArrival()).isEqualTo(LocalTime.of(8, 50));
        assertThat(plan.get(2).legDistanceMeters()).isEqualTo(2000);
    }

    @Test
    void siLaRutaPasaLaMedianocheIndicaElDiaSiguienteYQuedaFueraDeVentana() {
        List<RouteStop> route = List.of(
                stop(1, "Tarde", 18, 20, "AUTO", -33.40, -70.60),
                stop(2, "Madrugada", 18, 20, "AUTO", -33.41, -70.61));
        // 18:00 + 30 = 18:30; sale 18:40 y viaja 6 h: llega 00:40 del día siguiente.
        when(osrm.route(anyList())).thenReturn(Optional.of(List.of(leg(30, 1), leg(360, 1))));

        List<PlannedStopResponse> plan = timing.schedule(route, DEPOT_LAT, DEPOT_LNG, LocalTime.of(18, 0), 10);

        assertThat(plan.get(0).dayOffset()).isZero();
        assertThat(plan.get(1).estimatedArrival()).isEqualTo(LocalTime.of(0, 40));
        assertThat(plan.get(1).dayOffset()).isEqualTo(1);
        assertThat(plan.get(1).waitMinutes()).isZero();   // no "espera" a la ventana del día anterior
        assertThat(plan.get(1).outsideWindow()).isTrue();
    }

    @Test
    void unaRutaVaciaNoConsultaNada() {
        when(osrm.route(anyList())).thenReturn(Optional.of(List.of()));
        assertThat(timing.schedule(List.of(), DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10)).isEmpty();
    }

    /** HU-30: la parada consolidada informa sus envíos; una parada sin entrada tiene solo el suyo. */
    @Test
    void informaLosEnviosDeCadaParada() {
        RouteStop consolidated = stop(1, "A", 9, 11, "AUTO", -33.40, -70.60);
        RouteStop single = stop(2, "B", 9, 11, "AUTO", -33.41, -70.61);
        UUID other = UUID.randomUUID();
        when(osrm.route(anyList())).thenReturn(Optional.of(List.of(leg(10, 1), leg(10, 1))));

        List<PlannedStopResponse> plan = timing.schedule(List.of(consolidated, single),
                Map.of(consolidated.shipmentId(), List.of(consolidated.shipmentId(), other)),
                DEPOT_LAT, DEPOT_LNG, LocalTime.of(8, 0), 10);

        assertThat(plan.get(0).shipmentIds()).containsExactly(consolidated.shipmentId(), other);
        assertThat(plan.get(0).shipmentCount()).isEqualTo(2);
        assertThat(plan.get(1).shipmentIds()).containsExactly(single.shipmentId());
        assertThat(plan.get(1).shipmentCount()).isEqualTo(1);
    }
}
