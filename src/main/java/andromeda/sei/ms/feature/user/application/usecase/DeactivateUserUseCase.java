package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.feature.user.domain.exception.UserSelfDeactivationDeniedException;
import andromeda.sei.ms.shared.application.security.ProjectMembersChanged;
import andromeda.sei.ms.shared.application.security.UserPermissionsChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeactivateUserUseCase {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param callerUserId the authenticated user; deactivating oneself is refused
     *                     ({@code USER_SELF_DEACTIVATION_DENIED})
     */
    public void execute(String userId, String organizationId, String callerUserId) {
        User user = userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getId().equals(callerUserId)) {
            throw new UserSelfDeactivationDeniedException(user.getId());
        }

        user.setActive(false);
        userRepository.save(user);

        projectRepository.removeUserFromAllProjects(organizationId, userId);
        eventPublisher.publishEvent(new UserPermissionsChanged(organizationId, userId));
        eventPublisher.publishEvent(new ProjectMembersChanged(organizationId, null));
    }
}
