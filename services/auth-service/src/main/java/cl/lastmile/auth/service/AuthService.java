package cl.lastmile.auth.service;

import cl.lastmile.auth.dto.AuthDtos.*;
import cl.lastmile.auth.exception.InvalidCredentialsException;
import cl.lastmile.auth.exception.UserAlreadyExistsException;
import cl.lastmile.auth.model.User;
import cl.lastmile.auth.repository.UserRepository;
import cl.lastmile.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** Registro público: el rol siempre es CLIENT, sin importar lo que envíe el cliente. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = createUser(request.fullName(), request.email(), request.password(), User.Role.CLIENT);
        return buildAuthResponse(user);
    }

    /** Alta de usuarios con rol arbitrario. Solo la invoca UserController, protegido para ADMIN. */
    @Transactional
    public UserResponse createUserAsAdmin(CreateUserRequest request) {
        return UserResponse.from(createUser(request.fullName(), request.email(), request.password(), request.role()));
    }

    @Transactional
    public User createUser(String fullName, String email, String rawPassword, User.Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Ya existe un usuario con ese email");
        }

        User user = User.builder()
                .fullName(fullName)
                .email(normalizedEmail)
                // NUNCA se persiste la contraseña en texto plano
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas"));

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Usuario deshabilitado");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken) || !"refresh".equals(jwtUtil.extractTokenType(refreshToken))) {
            throw new InvalidCredentialsException("Refresh token inválido o expirado");
        }
        User user = userRepository.findById(java.util.UUID.fromString(jwtUtil.extractUserId(refreshToken)))
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado"));
        // HU-06: el frontend renueva la sesión solo; un usuario deshabilitado no debe seguir dentro.
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Usuario deshabilitado");
        }
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String access = jwtUtil.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole().name());
        String refresh = jwtUtil.generateRefreshToken(user.getId().toString());
        return new AuthResponse(access, refresh, "Bearer", jwtUtil.getAccessExpirationMs(), UserResponse.from(user));
    }
}
