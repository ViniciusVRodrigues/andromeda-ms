package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Enables or disables sync of a binding without touching previously synced data. */
@Component
@RequiredArgsConstructor
public class ToggleRepositorySyncUseCase {

    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;

    public RepoBindingOutput execute(String bindingId, boolean syncEnabled) {
        String orgId = TenantContextHolder.get();
        VcsRepoBinding binding = bindingRepository.findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new RepoBindingNotFoundException(bindingId));
        binding.setSyncEnabled(syncEnabled);
        VcsRepoBinding saved = bindingRepository.save(binding);
        return RepoBindingOutputs.of(saved,
                repositoryRepository.findByIdAndOrganizationId(saved.getRepositoryId(), orgId).orElse(null));
    }
}
