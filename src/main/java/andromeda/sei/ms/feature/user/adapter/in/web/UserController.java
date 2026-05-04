package andromeda.sei.ms.feature.user.adapter.in.web;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.CreateUserRequest;
import andromeda.sei.ms.feature.user.adapter.in.web.dto.UpdateUserRequest;
import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import andromeda.sei.ms.feature.user.application.port.in.CreateUserUseCase;
import andromeda.sei.ms.feature.user.application.port.in.DeactivateUserUseCase;
import andromeda.sei.ms.feature.user.application.port.in.ListUsersUseCase;
import andromeda.sei.ms.feature.user.application.port.in.UpdateUserUseCase;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management within the organization")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Create user", description = "Creates a new user in the organization")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return createUserUseCase.execute(request, TenantContextHolder.get());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "List users", description = "Lists all users in the organization")
    public List<UserResponse> list() {
        return listUsersUseCase.execute(TenantContextHolder.get());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Update user", description = "Updates user name and/or role")
    public UserResponse update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return updateUserUseCase.execute(id, request, TenantContextHolder.get());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "Deactivate user", description = "Soft-deletes a user (sets active = false)")
    public void deactivate(@PathVariable UUID id) {
        deactivateUserUseCase.execute(id, TenantContextHolder.get());
    }
}
