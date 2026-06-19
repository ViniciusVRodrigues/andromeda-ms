package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.application.dto.UpdateUserInput;
import andromeda.sei.ms.feature.user.application.dto.UserOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private final UserRepository userRepository;

    public UserOutput execute(UpdateUserInput input, String organizationId) {
        User user = userRepository.findByIdAndOrganizationId(input.id(), organizationId)
                .orElseThrow(() -> new UserNotFoundException(input.id()));

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
        return UserOutput.from(saved);
    }
}
