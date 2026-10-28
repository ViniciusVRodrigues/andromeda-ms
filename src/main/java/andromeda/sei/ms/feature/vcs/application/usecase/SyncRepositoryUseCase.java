package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.vcs.application.VcsIntegrations;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsSyncState;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral sync of one repository binding (vcs-core D5): repository → commits → pull
 * requests (with their work item links) → review events, through the provider ports only. Wrapped
 * in the two-write {@link IntegrationSyncLog} lifecycle ({@code SYNC_REPOS}).
 *
 * <p>The watermark ({@code binding.lastSyncAt}) is the instant the run <em>started</em>
 * (on-demand-refresh): a pull request updated while the run was paging would otherwise be missed.
 * Without a watermark the first sync is bounded by {@code backfillFrom}.
 *
 * <p>Derived pull request metrics are not computed here: the sync orchestrator recalculates every
 * metric of the affected projects once all bindings are done ({@code CalculatePullRequestMetricsUseCase}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncRepositoryUseCase {

    static final String SYNC_FAILED = "VCS_SYNC_FAILED";
    private static final int MAX_ERROR_DETAIL = 2000;

    private final IntegrationSyncLogRepository syncLogRepository;
    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsProviderRegistry providerRegistry;
    private final OrganizationRepository organizationRepository;
    private final VcsRepositoryContentSync contentSync;

    /**
     * @param backfillFrom first-sync window: when the binding was never synced, only commits and
     *                     pull requests created at or after it are read; {@code null} reads everything
     * @return items written (repository, commits, pull requests, review events)
     */
    public int execute(VcsCredentials credentials, VcsRepoBinding binding, SyncTrigger trigger, Instant backfillFrom) {
        Instant startedAt = Instant.now();
        IntegrationSyncLog syncLog = syncLogRepository.save(new IntegrationSyncLog(
                null, binding.getOrganizationId(), VcsIntegrations.integrationOf(binding.getProvider()),
                SyncOperation.SYNC_REPOS, SyncStatus.RUNNING, trigger, binding.getProjectId(), startedAt,
                null, null, 0, 0, null, null));
        markState(binding, VcsSyncState.RUNNING, startedAt);
        try {
            SyncOutcome outcome = sync(credentials, binding, startedAt, backfillFrom);
            if (outcome.reviewFailures() > 0) {
                // The sync stays SUCCESS but says how many pull requests came without their reviews.
                syncLog.setErrorMessage(outcome.reviewFailures() + (outcome.reviewFailures() == 1
                        ? " PR sem revisões lidas" : " PRs sem revisões lidas"));
            }
            finish(syncLog, startedAt, SyncStatus.SUCCESS, outcome.processed(), outcome.reviewFailures());
            return outcome.processed();
        } catch (RuntimeException e) {
            fail(syncLog, startedAt, e);
            markFailed(binding);
            throw e;
        }
    }

    private record SyncOutcome(int processed, int reviewFailures) {}

    private SyncOutcome sync(VcsCredentials credentials, VcsRepoBinding binding, Instant startedAt, Instant backfillFrom) {
        String orgId = binding.getOrganizationId();
        VcsProviderClient client = providerRegistry.client(binding.getProvider());
        VcsAuthorResolver authors = providerRegistry.authorResolver(binding.getProvider());
        Instant lastSyncAt = binding.getLastSyncAt();
        if (lastSyncAt == null) {
            /* First sync: fail only this binding when the credential cannot read code
             * (azure-repos-integration "Code read scope is validated"). */
            client.verifyAccess(credentials);
        }

        VcsRepository repository = refreshRepository(credentials, client, binding);
        VcsRepositoryContentSync.Context context = new VcsRepositoryContentSync.Context(
                credentials, client, authors, repository, workItemLinkConfig(orgId), binding);

        int commits = contentSync.syncCommits(context, lastSyncAt != null ? lastSyncAt : backfillFrom);
        List<VcsPullRequest> pullRequests = contentSync.syncPullRequests(context, lastSyncAt, backfillFrom);
        int events = contentSync.syncReviewEvents(context, pullRequests, lastSyncAt);

        try {
            authors.flushObserved(orgId);
        } catch (RuntimeException e) {
            log.warn("Could not register identities seen in pull requests: org={}: {}", orgId, e.getMessage());
        }

        binding.setLastSyncAt(startedAt);
        binding.setMissingBranches(List.copyOf(context.missingBranches()));
        binding.setSyncState(VcsSyncState.OK);
        binding.setSyncStateAt(Instant.now());
        saveSyncOutcome(binding);
        int processed = 1 + commits + pullRequests.size() + events;
        log.info("VCS sync completed: org={} project={} provider={} repo={} processed={}", orgId,
                binding.getProjectId(), binding.getProvider(), repository.getDisplayName(), processed);
        return new SyncOutcome(processed, context.reviewFailures().get());
    }

    /** Early save of the sync state ({@code RUNNING}). */
    private void markState(VcsRepoBinding binding, VcsSyncState state, Instant at) {
        binding.setSyncState(state);
        binding.setSyncStateAt(at);
        saveKeepingVersion(binding);
    }

    /**
     * The repository returns a new instance with the bumped {@code version}; this run keeps using the original
     * one, so it takes the version over or the next save would lose the optimistic lock.
     */
    private void saveKeepingVersion(VcsRepoBinding binding) {
        VcsRepoBinding saved = bindingRepository.save(binding);
        if (saved != null && saved != binding) binding.setVersion(saved.getVersion());
    }

    /**
     * Saves what the sync owns (watermark, missing branches, state, repository after a merge). A non-destructive
     * edit (sync on/off, production branch) may have saved the binding meanwhile and bumped its version, so the
     * outcome goes onto the stored binding instead of overwriting it. An unbound binding is left alone.
     */
    private void saveSyncOutcome(VcsRepoBinding binding) {
        VcsRepoBinding stored = bindingRepository
                .findByIdAndOrganizationIdAndActiveTrue(binding.getId(), binding.getOrganizationId())
                .orElse(null);
        if (stored == null) {
            log.warn("Binding {} was removed during its sync; outcome not saved", binding.getId());
            return;
        }
        if (stored != binding && !Objects.equals(stored.getVersion(), binding.getVersion())) {
            stored.setLastSyncAt(binding.getLastSyncAt());
            stored.setMissingBranches(binding.getMissingBranches());
            stored.setSyncState(binding.getSyncState());
            stored.setSyncStateAt(binding.getSyncStateAt());
            stored.setRepositoryId(binding.getRepositoryId());
            saveKeepingVersion(stored);
            binding.setVersion(stored.getVersion());
            return;
        }
        saveKeepingVersion(binding);
    }

    private void markFailed(VcsRepoBinding binding) {
        try {
            binding.setSyncState(VcsSyncState.FAILED);
            binding.setSyncStateAt(Instant.now());
            saveSyncOutcome(binding);
        } catch (RuntimeException e) {
            log.warn("Could not record the failed sync state of binding {}: {}", binding.getId(), e.getMessage());
        }
    }

    /**
     * Refreshes the repository metadata. A repository pending first sync gets its external id; if
     * another repository already carries that id, the binding moves to it and the pending one is
     * deactivated (design D2 merge).
     */
    VcsRepository refreshRepository(VcsCredentials credentials, VcsProviderClient client, VcsRepoBinding binding) {
        String orgId = binding.getOrganizationId();
        VcsRepository repository = repositoryRepository.findByIdAndOrganizationId(binding.getRepositoryId(), orgId)
                .orElseThrow(() -> new RepoNotFoundException(binding.getRepositoryId()));
        VcsRemoteRepository remote = client.getRepository(credentials, repository);

        if (repository.isPendingFirstSync()) {
            repository = mergeIntoExisting(repository, remote, binding);
        }
        repository.setExternalRepoId(remote.externalRepoId());
        if (remote.displayName() != null) repository.setDisplayName(remote.displayName());
        if (remote.externalProjectId() != null) repository.setExternalProjectId(remote.externalProjectId());
        repository.setDefaultBranch(remote.defaultBranch());
        repository.setExternalUpdatedAt(remote.updatedAt());
        repository.setActive(true);
        return repositoryRepository.save(repository);
    }

    private VcsRepository mergeIntoExisting(VcsRepository pending, VcsRemoteRepository remote, VcsRepoBinding binding) {
        return repositoryRepository.findByOrganizationIdAndProviderAndExternalRepoId(
                        pending.getOrganizationId(), pending.getProvider(), remote.externalRepoId())
                .filter(existing -> !existing.getId().equals(pending.getId()))
                .map(existing -> {
                    pending.setActive(false);
                    repositoryRepository.save(pending);
                    binding.setRepositoryId(existing.getId());
                    log.info("VCS repository {} resolved to existing repository {}; pending row deactivated",
                            pending.getDisplayName(), existing.getId());
                    return existing;
                })
                .orElse(pending);
    }

    private WorkItemLinkConfig workItemLinkConfig(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getVcsSettings)
                .map(settings -> settings.effectiveWorkItemLink())
                .orElseGet(WorkItemLinkConfig::defaults);
    }

    private void finish(IntegrationSyncLog syncLog, Instant startedAt, SyncStatus status, int processed, int failed) {
        Instant finishedAt = Instant.now();
        syncLog.setStatus(status);
        syncLog.setFinishedAt(finishedAt);
        syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        syncLog.setItemsProcessed(processed);
        syncLog.setItemsFailed(failed);
        syncLogRepository.save(syncLog);
    }

    private void fail(IntegrationSyncLog syncLog, Instant startedAt, RuntimeException e) {
        syncLog.setErrorCode(SYNC_FAILED);
        syncLog.setErrorMessage(e.getMessage());
        if (e instanceof WebClientResponseException wex) {
            syncLog.setHttpStatus(wex.getStatusCode().value());
            String body = wex.getResponseBodyAsString();
            syncLog.setErrorDetail(body.length() > MAX_ERROR_DETAIL ? body.substring(0, MAX_ERROR_DETAIL) : body);
            log.error("VCS provider HTTP error during sync: status={} body={}", wex.getStatusCode().value(),
                    syncLog.getErrorDetail());
        }
        finish(syncLog, startedAt, SyncStatus.FAILED, 0, 1);
    }
}
