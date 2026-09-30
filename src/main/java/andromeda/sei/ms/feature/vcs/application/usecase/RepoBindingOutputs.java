package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;

/** Binding + its repository → output (the display name lives on the repository). */
final class RepoBindingOutputs {

    private RepoBindingOutputs() {}

    static RepoBindingOutput of(VcsRepoBinding binding, VcsRepository repository) {
        return new RepoBindingOutput(binding.getId(), binding.getProjectId(), binding.getRepositoryId(),
                binding.getProvider(), repository != null ? repository.getDisplayName() : null,
                binding.isSyncEnabled(), binding.getLastSyncAt());
    }
}
