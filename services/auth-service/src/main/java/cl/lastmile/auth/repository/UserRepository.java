package cl.lastmile.auth.repository;

import cl.lastmile.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(User.Role role);

    /** HU-17: usuarios activos de un rol (por ejemplo, los conductores para asignar una ruta). */
    List<User> findByRoleAndEnabledTrueOrderByFullNameAsc(User.Role role);
}
