package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.BindGithubRepoInput;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoFullName;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoAlreadyBoundException;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoIdInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Creates or updates a repo binding for a project+repo pair -- requires {@code GITHUB_CONFIGURE}. */
@Component
@RequiredArgsConstructor
public class BindGithubRepoUseCase {

    private final GithubRepoBindingRepository githubRepoBindingRepository;

    public GithubRepoBinding execute(String projectId, BindGithubRepoInput input) {
        String orgId = TenantContextHolder.get();
        Instant now = Instant.now();

        // Reject a malformed identifier here rather than letting it through to the first sync
        // run: the binding key IS the owner/name the sync fetches by, so an unparseable value
        // produces a binding that can never sync.
        if (!GithubRepoFullName.isValid(input.repoId())) {
            throw new GithubRepoIdInvalidException(input.repoId());
        }

        // Exclusivity guard (design.md D3): one repo binds to at most ONE project per org.
        // Same-project rebind falls through to the idempotent upsert below.
        githubRepoBindingRepository.findByOrganizationIdAndRepoIdAndActiveTrue(orgId, input.repoId())
                .ifPresent(existing -> {
                    if (!existing.getProjectId().equals(projectId)) {
                        throw new GithubRepoAlreadyBoundException(input.repoId());
                    }
                });

        GithubRepoBinding binding = githubRepoBindingRepository
                .findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(orgId, projectId, input.repoId())
                .orElseGet(() -> new GithubRepoBinding(
                        null, orgId, projectId, input.repoId(), input.syncEnabled(), null, true, now, now, null));

        binding.setSyncEnabled(input.syncEnabled());

        return githubRepoBindingRepository.save(binding);
    }
}
