package andromeda.sei.ms.feature.user.application.service;

import andromeda.sei.ms.feature.user.application.port.in.DeactivateUserUseCase;
import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeactivateUserService implements DeactivateUserUseCase {

    private final UserRepository userRepository;

    @Override
    public void execute(UUID userId, UUID organizationId) {
        User user = userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        User deactivated = new User(user.id(), user.organizationId(), user.email(), user.passwordHash(),
                user.name(), user.roleId(), user.githubId(), false, user.createdAt());
        userRepository.save(deactivated);
    }
}
