package andromeda.sei.ms.feature.user.presentation;

import andromeda.sei.ms.feature.user.application.dto.CreateUserInput;
import andromeda.sei.ms.feature.user.application.dto.UpdateUserInput;
import andromeda.sei.ms.feature.user.application.dto.InviteUserInput;
import andromeda.sei.ms.feature.user.application.usecase.CreateUserUseCase;
import andromeda.sei.ms.feature.user.application.usecase.LinkAzureIdentityUseCase;
import andromeda.sei.ms.feature.user.application.usecase.DeactivateUserUseCase;
import andromeda.sei.ms.feature.user.application.usecase.InviteUserUseCase;
import andromeda.sei.ms.feature.user.application.usecase.ListUsersUseCase;
import andromeda.sei.ms.feature.user.application.usecase.UpdateUserUseCase;
import andromeda.sei.ms.feature.user.presentation.dto.CreateUserRequest;
import andromeda.sei.ms.feature.user.presentation.dto.LinkAzureIdentityRequest;
import andromeda.sei.ms.feature.user.presentation.dto.CreateUserResponse;
import andromeda.sei.ms.feature.user.presentation.dto.InviteUserRequest;
import andromeda.sei.ms.feature.user.presentation.dto.UpdateUserRequest;
import andromeda.sei.ms.feature.user.presentation.dto.UserResponse;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final InviteUserUseCase inviteUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    private final UserPresentationMapper mapper;
    private final LinkAzureIdentityUseCase linkAzureIdentityUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<CreateUserResponse> createUser(@RequestBody CreateUserRequest request) {
        String orgId = TenantContextHolder.get();
        CreateUserInput input = new CreateUserInput(request.email(), request.roleId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toCreateResponse(createUserUseCase.execute(input, orgId)));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<CreateUserResponse> inviteUser(@Valid @RequestBody InviteUserRequest request) {
        String orgId = TenantContextHolder.get();
        InviteUserInput input = new InviteUserInput(request.email(), request.roleName(), request.seniority());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toCreateResponse(inviteUserUseCase.execute(input, orgId)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        String orgId = TenantContextHolder.get();
        List<UserResponse> users = listUsersUseCase.execute(orgId)
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request) {
        String orgId = TenantContextHolder.get();
        UpdateUserInput input = new UpdateUserInput(id, request.name(), request.roleId(), request.seniority());
        return ResponseEntity.ok(mapper.toResponse(updateUserUseCase.execute(input, orgId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Void> deactivateUser(@PathVariable String id) {
        String orgId = TenantContextHolder.get();
        deactivateUserUseCase.execute(id, orgId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/azure-identity")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<Void> linkAzureIdentity(@PathVariable String id,
                                                   @RequestBody @Valid LinkAzureIdentityRequest request) {
        String orgId = TenantContextHolder.get();
        linkAzureIdentityUseCase.execute(orgId, id, request.azureId(), request.azureEmail());
        return ResponseEntity.noContent().build();
    }
}
