package cl.lastmile.delivery.repository;

import cl.lastmile.delivery.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByOrderByPlateAsc();

    List<Vehicle> findByActiveTrueOrderByPlateAsc();

    boolean existsByPlate(String plate);

    boolean existsByPlateAndIdNot(String plate, UUID id);
}
