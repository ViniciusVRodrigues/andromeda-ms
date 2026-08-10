package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.BindGithubRepoInput;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
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

        GithubRepoBinding binding = githubRepoBindingRepository
                .findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(orgId, projectId, input.repoId())
                .orElseGet(() -> new GithubRepoBinding(
                        null, orgId, projectId, input.repoId(), input.syncEnabled(), null, true, now, now, null));

        binding.setSyncEnabled(input.syncEnabled());

        return githubRepoBindingRepository.save(binding);
    }
}
