package andromeda.sei.ms.feature.github.infrastructure.vcs;

import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The organization's GitHub PAT, decrypted from {@code github_configs}. */
@Component
@RequiredArgsConstructor
public class GithubVcsCredentialsProvider implements VcsCredentialsProvider {

    private final GithubConnectionRepository githubConnectionRepository;
    private final EncryptionService encryptionService;

    @Override
    public VcsProvider provider() {
        return VcsProvider.GITHUB;
    }

    @Override
    public Optional<VcsCredentials> credentials(String organizationId) {
        return githubConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .map(connection -> new VcsCredentials(VcsProvider.GITHUB, organizationId,
                        encryptionService.decrypt(connection.getPersonalAccessToken()),
                        connection.getStatus() == GithubConnectionStatus.ERROR));
    }
}
