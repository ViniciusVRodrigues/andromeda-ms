package andromeda.sei.ms.feature.user.application.service;

import andromeda.sei.ms.feature.user.adapter.in.web.dto.UpdateUserRequest;
import andromeda.sei.ms.feature.user.adapter.in.web.dto.UserResponse;
import andromeda.sei.ms.feature.user.application.port.in.UpdateUserUseCase;
import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateUserService implements UpdateUserUseCase {

    private final UserRepository userRepository;

    @Override
    public UserResponse execute(UUID userId, UpdateUserRequest request, UUID organizationId) {
        User user = userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String newName = request.name() != null ? request.name() : user.name();
        UUID newRoleId = request.roleId() != null ? request.roleId() : user.roleId();

        User updated = new User(user.id(), user.organizationId(), user.email(), user.passwordHash(),
                newName, newRoleId, user.githubId(), user.active(), user.createdAt());
        return UserResponse.from(userRepository.save(updated));
    }
}
