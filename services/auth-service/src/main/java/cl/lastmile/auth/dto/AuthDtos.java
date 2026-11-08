package cl.lastmile.auth.dto;

import cl.lastmile.auth.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
            User.Role role
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
