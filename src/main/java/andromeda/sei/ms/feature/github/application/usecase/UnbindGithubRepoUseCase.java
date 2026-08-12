package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Soft-deletes a repo binding (design.md D8), keeping synced GitHub data. A binding that belongs
 * to a different project is "not found" within this project's scope (same 404, not 403/400).
 * The freed {@code repoId} becomes bindable to other projects immediately — exclusivity
 * considers {@code active = true} bindings only.
 */
@Component
@RequiredArgsConstructor
public class UnbindGithubRepoUseCase {

    private final GithubRepoBindingRepository githubRepoBindingRepository;

    public void execute(String projectId, String bindingId) {
        String orgId = TenantContextHolder.get();
        GithubRepoBinding binding = githubRepoBindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new GithubRepoBindingNotFoundException(bindingId));

        if (!binding.getProjectId().equals(projectId)) {
            throw new GithubRepoBindingNotFoundException(bindingId);
        }

        binding.setActive(false);
        binding.setSyncEnabled(false);
        githubRepoBindingRepository.save(binding);
    }
}
