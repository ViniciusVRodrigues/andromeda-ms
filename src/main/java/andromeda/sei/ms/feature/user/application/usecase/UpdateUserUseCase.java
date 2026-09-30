package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.application.dto.UpdateUserInput;
import andromeda.sei.ms.feature.user.application.dto.UserOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.feature.user.domain.exception.UserSelfRoleChangeDeniedException;
import andromeda.sei.ms.shared.application.security.UserPermissionsChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserOutput execute(UpdateUserInput input, String organizationId) {
        User user = userRepository.findByIdAndOrganizationId(input.id(), organizationId)
                .orElseThrow(() -> new UserNotFoundException(input.id()));

        boolean roleChanged = input.roleId() != null && !input.roleId().equals(user.getRoleId());
        // Echoing the current roleId on a self name edit is fine; only an actual change is refused.
        if (roleChanged && user.getId().equals(input.callerUserId())) {
            throw new UserSelfRoleChangeDeniedException(user.getId());
        }
        if (input.name() != null) {
            user.setName(input.name());
        }
        if (input.roleId() != null) {
            user.setRoleId(input.roleId());
        }
        if (input.seniority() != null) {
            user.setSeniority(input.seniority());
        }

        User saved = userRepository.save(user);
        if (roleChanged) {
            eventPublisher.publishEvent(new UserPermissionsChanged(organizationId, saved.getId()));
        }
        return UserOutput.from(saved);
    }
}
