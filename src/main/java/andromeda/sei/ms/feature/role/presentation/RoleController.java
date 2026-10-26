package andromeda.sei.ms.feature.role.presentation;

import andromeda.sei.ms.feature.role.application.dto.CloneRoleInput;
import andromeda.sei.ms.feature.role.application.dto.UpdateRolePermissionsInput;
import andromeda.sei.ms.feature.role.application.dto.CreateRoleInput;
import andromeda.sei.ms.feature.role.application.usecase.CloneRoleUseCase;
import andromeda.sei.ms.feature.role.application.usecase.CreateRoleUseCase;
import andromeda.sei.ms.feature.role.application.usecase.DeleteRoleUseCase;
import andromeda.sei.ms.feature.role.application.usecase.ListRolesUseCase;
import andromeda.sei.ms.feature.role.application.usecase.UpdateRolePermissionsUseCase;
import andromeda.sei.ms.feature.role.presentation.dto.CloneRoleRequest;
import andromeda.sei.ms.feature.role.presentation.dto.CreateRoleRequest;
import andromeda.sei.ms.feature.role.presentation.dto.RoleResponse;
import andromeda.sei.ms.feature.role.presentation.dto.UpdateRolePermissionsRequest;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final ListRolesUseCase listRolesUseCase;
    private final CreateRoleUseCase createRoleUseCase;
    private final CloneRoleUseCase cloneRoleUseCase;
    private final UpdateRolePermissionsUseCase updateRolePermissionsUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;
    private final RolePresentationMapper mapper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        String orgId = TenantContextHolder.get();
        List<RoleResponse> roles = listRolesUseCase.execute(orgId)
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(roles);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        String orgId = TenantContextHolder.get();
        CreateRoleInput input = new CreateRoleInput(request.name(), request.permissionCodes());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(createRoleUseCase.execute(input, orgId)));
    }

    @PostMapping("/clone")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> cloneRole(@RequestBody CloneRoleRequest request) {
        String orgId = TenantContextHolder.get();
        CloneRoleInput input = new CloneRoleInput(request.sourceRoleId(), request.newName());
        return ResponseEntity.ok(mapper.toResponse(cloneRoleUseCase.execute(input, orgId)));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> updatePermissions(
            @PathVariable String id,
            @RequestBody UpdateRolePermissionsRequest request) {
        String orgId = TenantContextHolder.get();
        UpdateRolePermissionsInput input = new UpdateRolePermissionsInput(id, request.add(), request.remove());
        return ResponseEntity.ok(mapper.toResponse(updateRolePermissionsUseCase.execute(input, orgId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<Void> deleteRole(@PathVariable String id) {
        String orgId = TenantContextHolder.get();
        deleteRoleUseCase.execute(id, orgId);
        return ResponseEntity.noContent().build();
    }
}
