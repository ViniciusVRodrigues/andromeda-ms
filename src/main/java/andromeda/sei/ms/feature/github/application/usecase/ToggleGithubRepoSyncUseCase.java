package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.ToggleGithubRepoSyncInput;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Enables/disables {@code syncEnabled} on an existing binding -- requires {@code GITHUB_CONFIGURE}. */
@Component
@RequiredArgsConstructor
public class ToggleGithubRepoSyncUseCase {

    private final GithubRepoBindingRepository githubRepoBindingRepository;

    public GithubRepoBinding execute(String bindingId, ToggleGithubRepoSyncInput input) {
        String orgId = TenantContextHolder.get();
        GithubRepoBinding binding = githubRepoBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new GithubRepoBindingNotFoundException(bindingId));

        binding.setSyncEnabled(input.syncEnabled());
        return githubRepoBindingRepository.save(binding);
    }
}
