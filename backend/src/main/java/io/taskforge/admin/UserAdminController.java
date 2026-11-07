package io.taskforge.admin;

import io.taskforge.admin.dto.AdminDtos.RoleChangeRequest;
import io.taskforge.auth.dto.AuthDtos.UserDto;
import io.taskforge.domain.RoleName;
import io.taskforge.web.RequestMeta;
import io.taskforge.web.RequestMetaResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin-only user role management. */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService service;
    private final RequestMetaResolver metaResolver;

    public UserAdminController(UserAdminService service, RequestMetaResolver metaResolver) {
        this.service = service;
        this.metaResolver = metaResolver;
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto changeRole(@PathVariable UUID id,
                              @Valid @RequestBody RoleChangeRequest req,
                              HttpServletRequest http) {
        RoleName role = RoleName.valueOf(req.role().toUpperCase());
        RequestMeta meta = metaResolver.resolve(http);
        return req.action().equalsIgnoreCase("GRANT")
                ? service.grantRole(id, role, meta)
                : service.revokeRole(id, role, meta);
    }
}
