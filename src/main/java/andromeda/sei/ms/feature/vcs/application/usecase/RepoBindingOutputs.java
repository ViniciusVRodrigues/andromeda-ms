package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;

import java.time.Instant;

/** Binding + its repository → output (the display name and default branch live on the repository). */
final class RepoBindingOutputs {

    private RepoBindingOutputs() {}

    static RepoBindingOutput of(VcsRepoBinding binding, VcsRepository repository, Integer backfillDays) {
        return of(binding, repository, backfillDays, false);
    }

    static RepoBindingOutput of(VcsRepoBinding binding, VcsRepository repository, Integer backfillDays,
                                boolean resynced) {
        String defaultBranch = repository != null ? repository.getDefaultBranch() : null;
        return new RepoBindingOutput(binding.getId(), binding.getProjectId(), binding.getRepositoryId(),
                binding.getProvider(), repository != null ? repository.getDisplayName() : null,
                binding.isSyncEnabled(), binding.getLastSyncAt(), defaultBranch,
                binding.effectiveBranches(defaultBranch), binding.effectiveProductionBranch(defaultBranch),
                binding.isBranchesConfigured(), binding.isFilterPullRequestsByBranch(),
                binding.getMissingBranches(), binding.effectiveSyncState(Instant.now()),
                VcsRepoBinding.MAX_BRANCHES, backfillDays, resynced);
    }

    /** The organization's backfill window in days; {@code null} when the organization is unknown. */
    static Integer backfillDays(OrganizationRepository organizationRepository, String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getSyncSettings)
                .map(settings -> settings != null ? settings : SyncSettings.defaults())
                .map(SyncSettings::backfillDays)
                .orElse(null);
    }
}
