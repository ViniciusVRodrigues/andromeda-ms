package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.shared.application.security.VcsIdentityLinked;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkGithubUseCase {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(String userId, String orgId, String githubId) {
        var user = userRepository.findByIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setGithubId(githubId);
        userRepository.save(user);
        eventPublisher.publishEvent(new VcsIdentityLinked(orgId, userId));
    }
}
