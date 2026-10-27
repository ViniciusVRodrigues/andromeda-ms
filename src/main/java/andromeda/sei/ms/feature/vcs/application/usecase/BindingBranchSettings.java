package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncCooldownService;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.vcs.application.VcsIntegrations;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRepositoryDataWiper;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBranchNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBranchesInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoSyncRunningException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsListBranchesFailedException;
import andromeda.sei.ms.shared.domain.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The branch rules shared by binding a repository and editing a binding (branches-por-vinculo D5):
 * validation (422), the destructive change (409 while a sync runs, wipe of the repository's data, back to
 * {@code PENDING}) and the immediate sync of the binding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BindingBranchSettings {

    /** What the caller asked to change; {@code null} fields are left as they are. */
    public record Change(Boolean syncEnabled, List<String> branches, String productionBranch, Boolean filter) {}

    /** The saved binding and whether its data was wiped to be read again. */
    public record Applied(VcsRepoBinding binding, boolean resynced) {}

    private final VcsProviderRegistry providerRegistry;
    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryDataWiper dataWiper;
    private final SyncOrchestrator syncOrchestrator;
    private final SyncCooldownService cooldownService;

    /** A new binding: fields set, nothing saved, nothing wiped; it starts {@code PENDING}. */
    public void configureNew(VcsRepoBinding binding, VcsRepository repository, Change change) {
        validate(binding, repository, change);
        setFields(binding, change);
        binding.setSyncState(VcsSyncState.PENDING);
        binding.setSyncStateAt(Instant.now());
    }

    /** An existing binding: validates, then saves; a change of the branch set or filter wipes and goes {@code PENDING}. */
    public Applied update(VcsRepoBinding binding, VcsRepository repository, Change change) {
        validate(binding, repository, change);
        String defaultBranch = repository.getDefaultBranch();
        boolean setChanged = change.branches() != null && !binding.sameBranchSet(change.branches(), defaultBranch);
        boolean filterChanged = change.filter() != null && change.filter() != binding.isFilterPullRequestsByBranch();
        boolean destructive = setChanged || filterChanged;

        if (destructive && binding.isSyncRunning(Instant.now())) {
            throw new RepoSyncRunningException(binding.getId());
        }
        setFields(binding, change);
        VcsRepoBinding saved = bindingRepository.save(binding);
        if (!destructive) return new Applied(saved, false);

        dataWiper.wipeRepository(saved.getOrganizationId(), saved.getRepositoryId());
        saved.setLastSyncAt(null);
        saved.setMissingBranches(List.of());
        saved.setSyncState(VcsSyncState.PENDING);
        saved.setSyncStateAt(Instant.now());
        saved = bindingRepository.save(saved);
        cooldownService.release(saved.getId());
        submit(saved);
        return new Applied(saved, true);
    }

    /** Submits the sync of this binding alone; a refusal (queue full, cooldown) leaves it {@code PENDING}. */
    public void submit(VcsRepoBinding binding) {
        if (!binding.isSyncEnabled()) return;
        try {
            syncOrchestrator.submit(SyncRequest.organization(binding.getOrganizationId(),
                    VcsIntegrations.integrationOf(binding.getProvider()),
                    List.of(SyncTargetResolver.toTarget(binding)), SyncTrigger.MANUAL));
        } catch (RuntimeException e) {
            log.warn("Could not submit the sync of binding {}: {}", binding.getId(), e.getMessage());
        }
    }

    private void setFields(VcsRepoBinding binding, Change change) {
        if (change.syncEnabled() != null) binding.setSyncEnabled(change.syncEnabled());
        if (change.branches() != null) {
            binding.setBranches(List.copyOf(change.branches()));
            binding.setProductionBranch(change.productionBranch() != null
                    ? change.productionBranch() : binding.getProductionBranch());
        } else if (change.productionBranch() != null && binding.isBranchesConfigured()) {
            binding.setProductionBranch(change.productionBranch());
        }
        if (change.filter() != null) binding.setFilterPullRequestsByBranch(change.filter());
    }

    /** 422 for a malformed selection or a branch the provider does not have; reads the provider only when branches come. */
    private void validate(VcsRepoBinding binding, VcsRepository repository, Change change) {
        String defaultBranch = repository.getDefaultBranch();
        if (change.branches() != null) {
            String production = change.productionBranch() != null ? change.productionBranch() : binding.getProductionBranch();
            VcsRepoBinding.validateBranches(change.branches(), production);
            assertExistInProvider(binding, repository, change.branches(), defaultBranch);
        } else if (change.productionBranch() != null) {
            List<String> current = binding.effectiveBranches(defaultBranch);
            if (!current.contains(change.productionBranch())) {
                throw new RepoBranchesInvalidException("the production branch must be one of the chosen branches");
            }
        }
    }

    private void assertExistInProvider(VcsRepoBinding binding, VcsRepository repository, List<String> chosen,
                                       String defaultBranch) {
        /* Only branches being added are checked: one already chosen that was deleted remotely (it shows up in
         * missingBranches) must not block other changes. */
        Set<String> current = new HashSet<>(binding.effectiveBranches(defaultBranch));
        List<String> wanted = chosen.stream().filter(name -> !current.contains(name)).toList();
        if (wanted.isEmpty()) return;
        VcsCredentials credentials = providerRegistry.credentialsProvider(binding.getProvider())
                .credentials(binding.getOrganizationId())
                .orElseThrow(() -> new VcsConnectionNotFoundException(binding.getProvider().name()));
        List<VcsBranch> existing;
        try {
            existing = providerRegistry.client(binding.getProvider()).listBranches(credentials, repository);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new VcsListBranchesFailedException(e);
        }
        Set<String> names = new HashSet<>();
        existing.forEach(branch -> names.add(branch.name()));
        List<String> missing = wanted.stream().filter(name -> !names.contains(name)).toList();
        if (!missing.isEmpty()) throw new RepoBranchNotFoundException(missing);
    }
}
