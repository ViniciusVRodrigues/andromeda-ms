package andromeda.sei.ms.feature.auth.application.service;

import andromeda.sei.ms.feature.auth.application.port.in.LinkGitHubUseCase;
import andromeda.sei.ms.feature.auth.application.port.out.UserAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LinkGitHubService implements LinkGitHubUseCase {

    private final UserAuthRepository userAuthRepository;

    @Override
    public void execute(String code, UUID userId, UUID organizationId) {
        // TODO: In production, exchange code for GitHub access token via OAuth2 flow,
        // retrieve GitHub user ID, then store it. Current implementation is a placeholder only.
        userAuthRepository.updateGithubId(userId, code);
    }
}
