package cl.lastmile.auth.config;

import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import cl.lastmile.auth.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el primer ADMIN a partir de BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD
 * (HU-05). Como el registro público solo crea CLIENT, sin esto no habría forma
 * de obtener un administrador en una instalación nueva.
 *
 * Es idempotente: si ya existe al menos un ADMIN, no hace nada (no recrea ni
 * cambia la contraseña aunque las variables sigan definidas).
 */
@Slf4j
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final AuthService authService;
    private final String email;
    private final String password;

    public BootstrapAdminRunner(
            UserRepository userRepository,
            AuthService authService,
            @Value("${bootstrap.admin.email:}") String email,
            @Value("${bootstrap.admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(User.Role.ADMIN)) {
            log.debug("Ya existe un ADMIN; se omite el bootstrap");
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn("No existe ningún ADMIN y BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD no están definidos: "
                    + "no se podrán crear usuarios con roles distintos de CLIENT");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (userRepository.existsByEmail(email.trim().toLowerCase())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL ya está registrado con otro rol: " + email
                    + ". Usa otro email o promueve ese usuario manualmente.");
        }

        authService.createUser("Administrador", email, password, User.Role.ADMIN);
        log.info("ADMIN inicial creado: {}", email.trim().toLowerCase());
    }
}
