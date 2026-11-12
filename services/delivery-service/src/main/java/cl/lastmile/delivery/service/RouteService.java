package cl.lastmile.delivery.service;

import cl.lastmile.delivery.algorithm.RouteOptimizationService.RouteStop;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer;
import cl.lastmile.delivery.algorithm.optimizer.UnloadTime;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.OptimizedRoute;
import cl.lastmile.delivery.algorithm.optimizer.RouteOptimizer.RouteProblem;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.model.Shipment;
import cl.lastmile.delivery.model.Vehicle;
import cl.lastmile.delivery.repository.ShipmentRepository;
import cl.lastmile.delivery.repository.VehicleRepository;
import cl.lastmile.delivery.service.StopConsolidator.DeliveryStop;
import cl.lastmile.delivery.service.StopConsolidator.StopLoad;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final ShipmentRepository shipmentRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteOptimizer routeOptimizer;

    /**
     * Ruta del día por paradas (HU-30), ordenada por el optimizador (HU-28).
     *
     * @param stops     paradas en orden; cada una con el envío representante como {@code shipmentId}
     *                  y la suma de bultos de sus envíos
     * @param loads     envíos de cada parada (el representante va primero) y la suma de su peso y
     *                  volumen (HU-29), por id del representante
     * @param optimizer {@code VRPTW} o {@code NEAREST_NEIGHBOR} (HU-28)
     * @param note      por qué se usó el nearest-neighbor, o null
     * @param vehicle   patente del camión con que se armó; null si no hay camiones registrados
     */
    public record ConsolidatedRoute(List<RouteStop> stops, Map<UUID, StopLoad> loads,
                                    String optimizer, String note, String vehicle) {
        public StopLoad load(RouteStop stop) {
            return loads.getOrDefault(stop.shipmentId(), StopLoad.single(stop.shipmentId()));
        }

        public List<UUID> shipmentIds(RouteStop stop) {
            return load(stop).shipmentIds();
        }
    }

    /**
     * Agrupa los envíos de la fecha en paradas (uno por local), las ordena con el
     * {@link RouteOptimizer} configurado y guarda la posición de la parada en cada uno de sus envíos.
     *
     * @param vehicleId camión de la ruta; null usa el activo de mayor capacidad (o ninguno si no hay)
     */
    @Transactional
    public ConsolidatedRoute buildRouteForDate(LocalDate date, double depotLat, double depotLng,
                                               LocalTime departure, int stopMinutes, String vehicleId) {
        return buildRouteForDate(date, depotLat, depotLng, departure, UnloadTime.fixed(stopMinutes), vehicleId);
    }

    /**
     * Igual, con el tiempo de descarga de HU-15 en cada parada: base + minutos por bulto + ajuste del
     * local. El optimizador lo usa para calcular a qué hora llega a cada parada.
     */
    @Transactional
    public ConsolidatedRoute buildRouteForDate(LocalDate date, double depotLat, double depotLng,
                                               LocalTime departure, UnloadTime unload, String vehicleId) {
        Vehicle vehicle = vehicle(vehicleId);
        List<Shipment> shipments = shipmentRepository.findByScheduledDate(date);
        List<DeliveryStop> stops = StopConsolidator.consolidate(shipments);

        Map<UUID, DeliveryStop> stopByRepresentative = new LinkedHashMap<>();
        stops.forEach(s -> stopByRepresentative.put(s.representative().getId(), s));

        OptimizedRoute optimized = routeOptimizer.optimize(problem(stops, depotLat, depotLng, departure, unload, vehicle));

        List<RouteStop> route = new ArrayList<>();
        Map<UUID, StopLoad> loads = new LinkedHashMap<>();
        for (RouteStop stop : optimized.stops()) {
            DeliveryStop group = stopByRepresentative.get(stop.shipmentId());
            for (Shipment shipment : group.shipments()) {
                shipment.setRoutePosition(stop.position());
                shipment.setStatus(Shipment.ShipmentStatus.ROUTED);
            }
            route.add(withPackages(stop, group.packageCount()));
            loads.put(stop.shipmentId(), group.load());
        }
        shipmentRepository.saveAll(shipments);

        return new ConsolidatedRoute(route, loads, optimized.optimizer(), optimized.note(),
                vehicle == null ? null : vehicle.getPlate());
    }

    /** El problema del optimizador para estas paradas, con el tiempo de descarga de cada una (HU-15). */
    static RouteProblem problem(List<DeliveryStop> stops, double depotLat, double depotLng, LocalTime departure,
                                UnloadTime unload, Vehicle vehicle) {
        return new RouteProblem(
                stops.stream().map(s -> new RouteOptimizer.Stop(s.representative(), s.weightKg(), s.volumeM3(),
                        unload.seconds(s.packageCount(), s.load().unloadExtraMinutes()))).toList(),
                depotLat, depotLng, departure, unload.baseMinutes(),
                vehicle == null ? null : new RouteOptimizer.Vehicle(vehicle.getPlate(), vehicle.getCapacityKg(),
                        vehicle.getCapacityM3(), vehicle.getShiftEnd()));
    }

    /** El camión pedido, o el activo de mayor capacidad si no se pide ninguno (null si no hay). */
    Vehicle vehicle(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            return vehicleRepository.findByActiveTrueOrderByPlateAsc().stream()
                    .max(Comparator.comparingDouble(Vehicle::getCapacityKg))
                    .orElse(null);
        }
        Vehicle vehicle = vehicleRepository.findById(RequestValues.uuid(vehicleId, "vehicleId"))
                .orElseThrow(() -> new EntityNotFoundException("Vehículo no encontrado"));
        if (!vehicle.isActive()) throw new BadRequestException("El vehículo " + vehicle.getPlate() + " está dado de baja");
        return vehicle;
    }

    private static RouteStop withPackages(RouteStop s, int packageCount) {
        return new RouteStop(s.shipmentId(), s.customerName(), s.address(), s.position(), s.windowStart(),
                s.windowEnd(), s.confidence(), s.hoursToAvoid(), s.stopType(), packageCount,
                s.orderPreference(), s.label(), s.latitude(), s.longitude());
    }
}
