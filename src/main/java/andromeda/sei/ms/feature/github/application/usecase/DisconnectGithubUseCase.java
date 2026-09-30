package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1:1 mirror of {@code DisconnectAzureUseCase} (design.md D6): soft-deactivate the connection
 * ({@code active = false} + {@code status = DISCONNECTED}), disable sync on all active bindings
 * (bindings stay active), synced data untouched. The scheduled job stops picking up the org
 * because it queries {@code status = CONNECTED && active} connections only.
 */
@Component
@RequiredArgsConstructor
public class DisconnectGithubUseCase {

    private final GithubConnectionRepository githubConnectionRepository;
    private final VcsRepoBindingRepository vcsRepoBindingRepository;

    public void execute() {
        String orgId = TenantContextHolder.get();
        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        connection.setActive(false);
        connection.setStatus(GithubConnectionStatus.DISCONNECTED);
        githubConnectionRepository.save(connection);

        List<VcsRepoBinding> bindings = vcsRepoBindingRepository
                .findAllActiveByOrganizationIdAndProvider(orgId, VcsProvider.GITHUB);
        bindings.forEach(b -> b.setSyncEnabled(false));
        vcsRepoBindingRepository.saveAll(bindings);
    }
}
