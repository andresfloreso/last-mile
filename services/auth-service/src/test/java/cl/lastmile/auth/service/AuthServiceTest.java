package cl.lastmile.auth.service;

import cl.lastmile.auth.dto.AuthDtos.CreateUserRequest;
import cl.lastmile.auth.dto.AuthDtos.RegisterRequest;
import cl.lastmile.auth.exception.UserAlreadyExistsException;
import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import cl.lastmile.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil("test_secret_key_with_at_least_32_characters!!", 60_000, 120_000);
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil);
    }

    private void saveReturnsWithId() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    @Test
    void registroPublicoSiempreCreaClient() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        saveReturnsWithId();

        var response = authService.register(new RegisterRequest("Ana", "ana@lastmile.cl", "clave12345"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(User.Role.CLIENT);
        assertThat(response.user().role()).isEqualTo("CLIENT");
    }

    @Test
    void normalizaElEmailAntesDeValidarDuplicados() {
        when(userRepository.existsByEmail("ana@lastmile.cl")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Ana", "  Ana@LastMile.cl ", "clave12345")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminPuedeCrearUsuariosConCualquierRol() {
        when(passwordEncoder.encode("clave12345")).thenReturn("hash");
        saveReturnsWithId();

        var created = authService.createUserAsAdmin(
                new CreateUserRequest("Pedro", "pedro@lastmile.cl", "clave12345", User.Role.DRIVER));

        assertThat(created.role()).isEqualTo("DRIVER");
        assertThat(created.email()).isEqualTo("pedro@lastmile.cl");
    }

    @Test
    void nuncaGuardaLaContrasenaEnTextoPlano() {
        when(passwordEncoder.encode("clave12345")).thenReturn("$2a$12$hash");
        saveReturnsWithId();

        authService.register(new RegisterRequest("Ana", "ana@lastmile.cl", "clave12345"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$12$hash");
    }
}
