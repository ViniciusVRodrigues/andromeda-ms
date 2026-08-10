package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.GithubConnectionStatusOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Returns connection + binding status -- requires {@code GITHUB_READ}. */
@Component
@RequiredArgsConstructor
public class GetGithubConnectionStatusUseCase {

    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubRepoBindingRepository githubRepoBindingRepository;

    public GithubConnectionStatusOutput execute() {
        String orgId = TenantContextHolder.get();
        GithubConnection connection = githubConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new GithubConnectionNotFoundException(orgId));

        var bindings = githubRepoBindingRepository.findAllByOrganizationIdAndActiveTrue(orgId).stream()
                .map(b -> new GithubConnectionStatusOutput.BindingSummary(
                        b.getId(), b.getProjectId(), b.getRepoId(), b.isSyncEnabled(), b.getLastSyncAt()))
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
