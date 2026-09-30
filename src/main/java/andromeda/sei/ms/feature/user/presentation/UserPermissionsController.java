package andromeda.sei.ms.feature.user.presentation;

import andromeda.sei.ms.feature.user.application.dto.PermissionOverrideInput;
import andromeda.sei.ms.feature.user.application.dto.ProjectMemberPermissionsOutput;
import andromeda.sei.ms.feature.user.application.dto.ReplacePermissionOverridesInput;
import andromeda.sei.ms.feature.user.application.dto.UserPermissionsOutput;
import andromeda.sei.ms.feature.user.application.usecase.GetUserPermissionsUseCase;
import andromeda.sei.ms.feature.user.application.usecase.ListProjectMemberPermissionsUseCase;
import andromeda.sei.ms.feature.user.application.usecase.ReplaceUserPermissionOverridesUseCase;
import andromeda.sei.ms.feature.user.presentation.dto.ReplacePermissionOverridesRequest;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Per-user permission overrides (project-scoped-permissions, user-permission-overrides). */
@Tag(name = "User permissions", description = "Role preset, per-user overrides and effective permissions")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserPermissionsController {

    private final GetUserPermissionsUseCase getUserPermissionsUseCase;
    private final ReplaceUserPermissionOverridesUseCase replaceUserPermissionOverridesUseCase;
    private final ListProjectMemberPermissionsUseCase listProjectMemberPermissionsUseCase;
    private final CurrentUserPermissions currentUserPermissions;

    @Operation(summary = "Role preset, overrides and effective permissions of a user",
            description = "Allowed to USER_MANAGE holders or to the user themselves.")
    @ApiResponse(responseCode = "200", description = "Permissions returned")
    @ApiResponse(responseCode = "403", description = "Neither USER_MANAGE nor the user themselves")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @GetMapping("/users/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGE') or #id == authentication.name")
    public ResponseEntity<UserPermissionsOutput> get(@PathVariable String id) {
        return ResponseEntity.ok(getUserPermissionsUseCase.execute(TenantContextHolder.get(), id));
    }

    @Operation(summary = "Replace the full override list of a user",
            description = "Optimistic locking by version; only codes the caller holds at the same scope can be "
                    + "added or removed.")
    @ApiResponse(responseCode = "200", description = "Overrides replaced; body as GET")
    @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE or PERMISSION_ESCALATION_DENIED")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND or PROJECT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "USER_CONFLICT")
    @ApiResponse(responseCode = "422", description = "PERMISSION_CODE_INVALID or PERMISSION_NOT_PROJECT_SCOPABLE")
    @PutMapping("/users/{id}/permission-overrides")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserPermissionsOutput> replace(@PathVariable String id,
                                                        @RequestBody ReplacePermissionOverridesRequest request) {
        List<PermissionOverrideInput> overrides = request.overrides() == null ? List.of()
                : request.overrides().stream()
                        .map(o -> o == null ? null : new PermissionOverrideInput(o.code(), o.effect(), o.projectId()))
                        .toList();
        return ResponseEntity.ok(replaceUserPermissionOverridesUseCase.execute(new ReplacePermissionOverridesInput(
                currentUserPermissions.require(), id, overrides, request.version())));
    }

    @Operation(summary = "Members of a project with their effective project codes and project overrides")
    @ApiResponse(responseCode = "200", description = "Members listed")
    @ApiResponse(responseCode = "403", description = "Missing USER_MANAGE or project not accessible")
    @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND")
    @GetMapping("/projects/{projectId}/member-permissions")
    @PreAuthorize("@perm.onProject(#projectId, 'USER_MANAGE')")
    public ResponseEntity<List<ProjectMemberPermissionsOutput>> memberPermissions(@PathVariable String projectId) {
        return ResponseEntity.ok(listProjectMemberPermissionsUseCase.execute(TenantContextHolder.get(), projectId));
    }
}
