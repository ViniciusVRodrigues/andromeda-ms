package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Soft-deletes a repository binding, keeping synced data. A binding of another project is "not
 * found" within this project's scope. The freed repository is bindable elsewhere immediately:
 * exclusivity only considers active bindings.
 */
@Component
@RequiredArgsConstructor
public class UnbindRepositoryUseCase {

    private final VcsRepoBindingRepository bindingRepository;

    public void execute(String projectId, String bindingId) {
        String orgId = TenantContextHolder.get();
        VcsRepoBinding binding = bindingRepository.findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .filter(found -> found.getProjectId().equals(projectId))
                .orElseThrow(() -> new RepoBindingNotFoundException(bindingId));
        binding.setActive(false);
        binding.setSyncEnabled(false);
        bindingRepository.save(binding);
    }
}
