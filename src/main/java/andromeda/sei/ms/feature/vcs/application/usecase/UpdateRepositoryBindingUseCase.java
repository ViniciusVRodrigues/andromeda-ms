package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Edits a binding: sync on/off and, optionally, its branches, production branch and pull request filter
 * (branches-por-vinculo D5). Changing the effective branch set or the filter deletes this repository's
 * synced data and reads it again; changing only the production branch (or the sync switch) deletes nothing.
 */
@Component
@RequiredArgsConstructor
public class UpdateRepositoryBindingUseCase {

    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;
    private final OrganizationRepository organizationRepository;
    private final BindingBranchSettings branchSettings;

    public RepoBindingOutput execute(String bindingId, BindingBranchSettings.Change change) {
        String orgId = TenantContextHolder.get();
        VcsRepoBinding binding = bindingRepository.findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new RepoBindingNotFoundException(bindingId));
        VcsRepository repository = repositoryRepository.findByIdAndOrganizationId(binding.getRepositoryId(), orgId)
                .orElseThrow(() -> new RepoNotFoundException(binding.getRepositoryId()));

        BindingBranchSettings.Applied applied = branchSettings.update(binding, repository, change);
        return RepoBindingOutputs.of(applied.binding(), repository,
                RepoBindingOutputs.backfillDays(organizationRepository, orgId), applied.resynced());
    }
}
