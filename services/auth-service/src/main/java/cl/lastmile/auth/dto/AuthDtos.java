package cl.lastmile.auth.dto;

import cl.lastmile.auth.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    /**
     * Registro público. No incluye rol: cualquier campo "role" que envíe el
     * cliente se ignora y el usuario siempre se crea como CLIENT (HU-05).
     */
    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotNull @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password
    ) {}

    /** Alta de usuarios por un ADMIN, con cualquier rol (POST /api/users). */
    public record CreateUserRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotNull @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
            @NotNull User.Role role
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInMs,
            UserResponse user
    ) {}

    public record UserResponse(
            String id,
            String fullName,
            String email,
            String role
    ) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId().toString(), u.getFullName(), u.getEmail(), u.getRole().name());
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {}
}
