package io.taskforge.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AdminDtos {

    private AdminDtos() {
    }

    /** Grant or revoke a single role on a user. Values are validated up front so
     *  a bad enum returns 422, not a 500 from {@code valueOf}. */
    public record RoleChangeRequest(
            @NotBlank @Pattern(regexp = "ADMIN|DEVELOPER|VIEWER",
                    message = "role must be ADMIN, DEVELOPER or VIEWER") String role,
            @NotBlank @Pattern(regexp = "GRANT|REVOKE",
                    message = "action must be GRANT or REVOKE") String action
    ) {}
}
