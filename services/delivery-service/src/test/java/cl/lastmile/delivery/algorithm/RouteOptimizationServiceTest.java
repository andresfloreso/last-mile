package cl.lastmile.delivery.algorithm;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.geo.OsrmRoutingClient;
import cl.lastmile.delivery.model.Customer;
import cl.lastmile.delivery.model.DeliveryAttempt;
import cl.lastmile.delivery.model.DeliveryAttempt.AttemptOutcome;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Shipment.OrderPreference;
import cl.lastmile.delivery.repository.DeliveryAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HU-04: pruebas base del orden de la ruta (sin modificar el servicio). Usa el
 * aprendizaje real, un repositorio de intentos en memoria y OSRM simulado:
 * sin matriz (respaldo Haversine) o con una matriz dada.
 */
class RouteOptimizationServiceTest {

    private static final double DEPOT_LAT = -33.4500;
    private static final double DEPOT_LNG = -70.6500;
    /** ~0,009° de latitud ≈ 1 km. */
    private static final double KM = 0.009;

    private final Map<UUID, List<DeliveryAttempt>> attemptsByCustomer = new HashMap<>();
    private final DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
    private final OsrmRoutingClient osrm = mock(OsrmRoutingClient.class);
    private RouteOptimizationService service;

    @BeforeEach
    void setUp() {
        when(attempts.findByCustomerId(any())).thenAnswer(inv -> attemptsByCustomer.getOrDefault(inv.getArgument(0), List.of()));
        when(osrm.distanceMatrix(anyList())).thenReturn(Optional.empty());
        service = new RouteOptimizationService(new TimeWindowLearningService(), attempts, osrm);
    }

    private static Shipment shipment(String name, Double lat, Double lng, OrderPreference preference) {
        Customer customer = Customer.builder()
                .id(UUID.randomUUID()).fullName(name).address(name + " 100").latitude(lat).longitude(lng).build();
        return Shipment.builder()
                .id(UUID.randomUUID()).trackingCode("T-" + name).customer(customer)
                .scheduledDate(LocalDate.of(2026, 12, 1)).orderPreference(preference).build();
    }

    /** Local a {@code km} kilómetros al norte del depósito. */
    private static Shipment north(String name, double km) {
        return shipment(name, DEPOT_LAT + km * KM, DEPOT_LNG, OrderPreference.AUTO);
    }

    /** Historial: el local recibe siempre a las {@code hour} y {@code hour + 1}. */
    private void receivesAt(Shipment s, int hour) {
        List<DeliveryAttempt> list = new ArrayList<>();
        for (int h : new int[]{hour, hour + 1}) {
            for (int i = 0; i < 3; i++) {
                list.add(DeliveryAttempt.builder()
                        .attemptedAt(LocalDate.of(2026, 11, 2 + i).atTime(h, 15).atZone(ZoneId.of("America/Santiago")).toInstant())
                        .outcome(AttemptOutcome.ACCEPTED)
                        .build());
            }
        }
        attemptsByCustomer.put(s.getCustomer().getId(), list);
    }

    private static List<String> names(List<RouteStop> route) {
        return route.stream().map(RouteStop::customerName).toList();
    }

    @Test
    void sinEnviosLaRutaEstaVacia() {
        assertThat(service.buildRoute(List.of(), DEPOT_LAT, DEPOT_LNG)).isEmpty();
    }

    @Test
    void sinHistorialOrdenaPorVecinoMasCercanoDesdeElDeposito() {
        List<RouteStop> route = service.buildRoute(
                List.of(north("C", 3), north("A", 1), north("B", 2)), DEPOT_LAT, DEPOT_LNG);

        assertThat(names(route)).containsExactly("A", "B", "C");
        assertThat(route).extracting(RouteStop::position).containsExactly(1, 2, 3);
    }

    @Test
    void laVentanaRealAprendidaPesaMasQueLaDistancia() {
        Shipment nearButAfternoon = north("Cerca-tarde", 1);
        Shipment farButMorning = north("Lejos-mañana", 5);
        receivesAt(nearButAfternoon, 16);
        receivesAt(farButMorning, 9);

        List<RouteStop> route = service.buildRoute(List.of(nearButAfternoon, farButMorning), DEPOT_LAT, DEPOT_LNG);

        assertThat(names(route)).containsExactly("Lejos-mañana", "Cerca-tarde");
        assertThat(route.get(0).windowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(route.get(1).windowStart()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void primeraYUltimaSeRespetanAunqueLaDistanciaDigaOtraCosa() {
        Shipment first = shipment("Primera", DEPOT_LAT + 9 * KM, DEPOT_LNG, OrderPreference.FIRST);
        Shipment auto = north("Auto", 5);
        Shipment last = shipment("Última", DEPOT_LAT + 0.5 * KM, DEPOT_LNG, OrderPreference.LAST);

        List<RouteStop> route = service.buildRoute(List.of(last, auto, first), DEPOT_LAT, DEPOT_LNG);

        assertThat(names(route)).containsExactly("Primera", "Auto", "Última");
        assertThat(route).extracting(RouteStop::orderPreference).containsExactly("FIRST", "AUTO", "LAST");
    }

    @Test
    void variasPrimerasSeOrdenanPorVentanaYVariasUltimasPorCercania() {
        Shipment firstAfternoon = shipment("P-tarde", DEPOT_LAT + KM, DEPOT_LNG, OrderPreference.FIRST);
        Shipment firstMorning = shipment("P-mañana", DEPOT_LAT + 2 * KM, DEPOT_LNG, OrderPreference.FIRST);
        receivesAt(firstAfternoon, 15);
        receivesAt(firstMorning, 10);
        // Tras la última "primera" (P-tarde, 1 km al norte), la "última" más cercana es la de 3 km.
        Shipment lastFar = shipment("U-lejos", DEPOT_LAT + 8 * KM, DEPOT_LNG, OrderPreference.LAST);
        Shipment lastNear = shipment("U-cerca", DEPOT_LAT + 3 * KM, DEPOT_LNG, OrderPreference.LAST);

        List<RouteStop> route = service.buildRoute(
                List.of(lastFar, firstAfternoon, lastNear, firstMorning), DEPOT_LAT, DEPOT_LNG);

        assertThat(names(route)).containsExactly("P-mañana", "P-tarde", "U-cerca", "U-lejos");
    }

    @Test
    void usaLaDistanciaPorCalleDeOsrmCuandoEstaDisponible() {
        Shipment a = north("A", 1); // más cerca en línea recta...
        Shipment b = north("B", 2);
        // ...pero por calle A queda a 9 km y B a 2 km. Índices: 0 = depósito, 1 = A, 2 = B.
        double[][] matrix = {
                {0, 9000, 2000},
                {9000, 0, 1000},
                {2000, 1000, 0},
        };
        when(osrm.distanceMatrix(anyList())).thenReturn(Optional.of(matrix));

        List<RouteStop> route = service.buildRoute(List.of(a, b), DEPOT_LAT, DEPOT_LNG);

        assertThat(names(route)).containsExactly("B", "A");
    }

    @Test
    void unLocalSinCoordenadasNoRompeLaRutaYQuedaAlFinalDeSuFranja() {
        Shipment withoutCoords = shipment("Sin-ubicación", null, null, OrderPreference.AUTO);

        List<RouteStop> route = service.buildRoute(
                List.of(withoutCoords, north("A", 1), north("B", 2)), DEPOT_LAT, DEPOT_LNG);

        assertThat(names(route)).containsExactly("A", "B", "Sin-ubicación");
        assertThat(route.get(2).latitude()).isNull();
    }

    @Test
    void cadaParadaLlevaSuVentanaConfianzaYDatosDelEnvio() {
        Shipment s = north("Bar", 1);
        s.setStopType(Shipment.StopType.PICKUP);
        s.setPackageCount(4);
        s.setLabel("Frágil");
        receivesAt(s, 13);

        RouteStop stop = service.buildRoute(List.of(s), DEPOT_LAT, DEPOT_LNG).get(0);

        assertThat(stop.shipmentId()).isEqualTo(s.getId());
        assertThat(stop.address()).isEqualTo("Bar 100");
        assertThat(stop.windowStart()).isEqualTo(LocalTime.of(13, 0));
        assertThat(stop.windowEnd()).isEqualTo(LocalTime.of(15, 0));
        assertThat(stop.confidence()).isEqualTo(1.0);
        assertThat(stop.hoursToAvoid()).isEmpty();
        assertThat(stop.stopType()).isEqualTo("PICKUP");
        assertThat(stop.packageCount()).isEqualTo(4);
        assertThat(stop.label()).isEqualTo("Frágil");
        assertThat(stop.latitude()).isEqualTo(s.getCustomer().getLatitude());
        assertThat(stop.longitude()).isEqualTo(DEPOT_LNG);
    }
}
