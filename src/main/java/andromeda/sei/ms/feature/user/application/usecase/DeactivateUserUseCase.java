package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeactivateUserUseCase {

    private final UserRepository userRepository;

    public void execute(String userId, String organizationId) {
        User user = userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setActive(false);
        userRepository.save(user);
    }
}
