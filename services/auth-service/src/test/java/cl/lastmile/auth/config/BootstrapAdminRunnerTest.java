package cl.lastmile.auth.config;

import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import cl.lastmile.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {

    @Mock UserRepository userRepository;
    @Mock AuthService authService;

    private BootstrapAdminRunner runner(String email, String password) {
        return new BootstrapAdminRunner(userRepository, authService, email, password);
    }

    @Test
    void creaElAdminSiNoExisteNinguno() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@lastmile.cl")).thenReturn(false);

        runner("admin@lastmile.cl", "clave-admin-123").run(null);

        verify(authService).createUser("Administrador", "admin@lastmile.cl", "clave-admin-123", User.Role.ADMIN);
    }

    @Test
    void noHaceNadaSiYaExisteUnAdmin() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(true);

        runner("admin@lastmile.cl", "clave-admin-123").run(null);

        verify(authService, never()).createUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    void noHaceNadaSinVariablesDeEntorno() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);

        runner("", "").run(null);

        verify(authService, never()).createUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    void fallaConContrasenaCorta() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner("admin@lastmile.cl", "corta").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8 caracteres");
    }

    @Test
    void fallaSiElEmailYaExisteConOtroRol() {
        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@lastmile.cl")).thenReturn(true);

        assertThatThrownBy(() -> runner("Admin@LastMile.cl", "clave-admin-123").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(authService, never()).createUser(anyString(), anyString(), anyString(), any());
    }
}
