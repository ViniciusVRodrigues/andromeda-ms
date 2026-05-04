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
        // In a real implementation, exchange code for GitHub access token and get GitHub user id
        // For now, store the code as githubId (placeholder)
        userAuthRepository.updateGithubId(userId, code);
    }
}
