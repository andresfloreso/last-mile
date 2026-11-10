package cl.lastmile.delivery.service;

import cl.lastmile.delivery.dto.DeliveryDtos.VehicleRequest;
import cl.lastmile.delivery.dto.DeliveryDtos.VehicleResponse;
import cl.lastmile.delivery.exception.BadRequestException;
import cl.lastmile.delivery.exception.ConflictException;
import cl.lastmile.delivery.model.Vehicle;
import cl.lastmile.delivery.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Camiones de la distribuidora con su capacidad y jornada (HU-29). */
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(boolean includeInactive) {
        List<Vehicle> vehicles = includeInactive
                ? vehicleRepository.findAllByOrderByPlateAsc()
                : vehicleRepository.findByActiveTrueOrderByPlateAsc();
        return vehicles.stream().map(VehicleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(String vehicleId) {
        return VehicleResponse.from(find(vehicleId));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest req) {
        String plate = normalizePlate(req.plate());
        if (vehicleRepository.existsByPlate(plate)) {
            throw new ConflictException("Ya existe un vehículo con la patente " + plate);
        }
        Vehicle vehicle = new Vehicle();
        apply(vehicle, plate, req);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(String vehicleId, VehicleRequest req) {
        Vehicle vehicle = find(vehicleId);
        String plate = normalizePlate(req.plate());
        if (vehicleRepository.existsByPlateAndIdNot(plate, vehicle.getId())) {
            throw new ConflictException("Ya existe un vehículo con la patente " + plate);
        }
        apply(vehicle, plate, req);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    /** "kxpt 21" → "KXPT21"; "kxpt-21" → "KXPT-21". */
    static String normalizePlate(String raw) {
        return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static void apply(Vehicle vehicle, String plate, VehicleRequest req) {
        if (!req.shiftStart().isBefore(req.shiftEnd())) {
            throw new BadRequestException("La jornada debe terminar después de empezar (shiftStart < shiftEnd)");
        }
        vehicle.setPlate(plate);
        vehicle.setName(req.name() == null || req.name().isBlank() ? null : req.name().trim());
        vehicle.setCapacityKg(req.capacityKg());
        vehicle.setCapacityM3(req.capacityM3());
        vehicle.setShiftStart(req.shiftStart());
        vehicle.setShiftEnd(req.shiftEnd());
        vehicle.setActive(req.active() == null || req.active());
    }

    private Vehicle find(String vehicleId) {
        UUID id = RequestValues.uuid(vehicleId, "vehicleId");
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehículo no encontrado"));
    }
}
