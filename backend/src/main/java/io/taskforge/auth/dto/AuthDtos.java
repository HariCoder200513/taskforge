package io.taskforge.auth.dto;

import io.taskforge.domain.Role;
import io.taskforge.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request/response payloads for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotBlank @Size(max = 200) String name
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record UserDto(
            String id,
            String email,
            String name,
            List<String> roles,
            String status
    ) {
        public static UserDto from(User user) {
            return new UserDto(
                    user.getId().toString(),
                    user.getEmail(),
                    user.getName(),
                    user.getRoles().stream().map(Role::getName).sorted().toList(),
                    user.getStatus().name());
        }
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserDto user
    ) {}

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {}
}
