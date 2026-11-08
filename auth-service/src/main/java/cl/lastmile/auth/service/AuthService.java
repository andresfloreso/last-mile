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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Ya existe un usuario con ese email");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email().toLowerCase())
                // NUNCA se persiste la contraseña en texto plano
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role() != null ? request.role() : User.Role.OPERATOR)
                .build();

        userRepository.save(user);
        return buildAuthResponse(user);
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
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String access = jwtUtil.generateAccessToken(user.getId().toString(), user.getEmail(), user.getRole().name());
        String refresh = jwtUtil.generateRefreshToken(user.getId().toString());
        return new AuthResponse(access, refresh, "Bearer", jwtUtil.getAccessExpirationMs(), UserResponse.from(user));
    }
}
