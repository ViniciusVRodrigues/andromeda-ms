package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Returns connection + GitHub binding status -- requires {@code REPOS_READ}. Each binding's
 * {@code repoId} is the repository display name ({@code owner/name}), as before vcs-core.
 */
@Component
@RequiredArgsConstructor
public class GetGithubConnectionStatusUseCase {

    private final GithubConnectionRepository githubConnectionRepository;
    private final VcsRepoBindingRepository vcsRepoBindingRepository;
    private final VcsRepositoryRepository vcsRepositoryRepository;

    public GithubConnectionStatusOutput execute() {
        String orgId = TenantContextHolder.get();
        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        List<VcsRepoBinding> active = vcsRepoBindingRepository
                .findAllActiveByOrganizationIdAndProvider(orgId, VcsProvider.GITHUB);
        Map<String, String> nameById = vcsRepositoryRepository
                .findAllByOrganizationIdAndIdIn(orgId, active.stream().map(VcsRepoBinding::getRepositoryId).toList())
                .stream()
                .filter(repo -> repo.getDisplayName() != null)
                .collect(Collectors.toMap(VcsRepository::getId, VcsRepository::getDisplayName, (a, b) -> a));
        var bindings = active.stream()
                .map(b -> new GithubConnectionStatusOutput.BindingSummary(b.getId(), b.getProjectId(),
                        nameById.getOrDefault(b.getRepositoryId(), b.getRepositoryId()), b.isSyncEnabled(),
                        b.getLastSyncAt()))
                .toList();

        return new GithubConnectionStatusOutput(
                connection.getStatus(),
                connection.getAuthorizedByUserId(),
                connection.getLastSuccessAt(),
                connection.getConsecutiveFailures(),
                bindings
        );
    }
}
