package andromeda.sei.ms.feature.role.adapter.in.web;

import andromeda.sei.ms.feature.role.adapter.in.web.dto.CloneRoleRequest;
import andromeda.sei.ms.feature.role.adapter.in.web.dto.RoleResponse;
import andromeda.sei.ms.feature.role.adapter.in.web.dto.UpdateRolePermissionsRequest;
import andromeda.sei.ms.feature.role.application.port.in.CloneRoleUseCase;
import andromeda.sei.ms.feature.role.application.port.in.DeleteRoleUseCase;
import andromeda.sei.ms.feature.role.application.port.in.ListRolesUseCase;
import andromeda.sei.ms.feature.role.application.port.in.UpdateRolePermissionsUseCase;
import andromeda.sei.ms.shared.tenant.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role management within the organization")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final ListRolesUseCase listRolesUseCase;
    private final CloneRoleUseCase cloneRoleUseCase;
    private final UpdateRolePermissionsUseCase updateRolePermissionsUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;

    @GetMapping
    @Operation(summary = "List roles", description = "Lists all roles available for the current organization (global + custom)")
    public List<RoleResponse> list() {
        UUID orgId = TenantContextHolder.get();
        return listRolesUseCase.execute(orgId).stream().map(RoleResponse::from).toList();
    }

    @PostMapping("/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Clone role", description = "Creates a custom role based on an existing one")
    public RoleResponse clone(@Valid @RequestBody CloneRoleRequest request) {
        UUID orgId = TenantContextHolder.get();
        return RoleResponse.from(cloneRoleUseCase.execute(request, orgId));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Update role permissions", description = "Adds or removes permissions from a custom role")
    public RoleResponse updatePermissions(@PathVariable UUID id,
                                          @RequestBody UpdateRolePermissionsRequest request) {
        UUID orgId = TenantContextHolder.get();
        return RoleResponse.from(updateRolePermissionsUseCase.execute(id, request, orgId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Delete role", description = "Deletes a custom role (cannot delete default roles or roles with assigned users)")
    public void delete(@PathVariable UUID id) {
        UUID orgId = TenantContextHolder.get();
        deleteRoleUseCase.execute(id, orgId);
    }
}
