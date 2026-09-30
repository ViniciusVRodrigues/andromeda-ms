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
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;

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
        try {
            int processed = sync(credentials, binding, startedAt, backfillFrom);
            finish(syncLog, startedAt, SyncStatus.SUCCESS, processed, 0);
            return processed;
        } catch (RuntimeException e) {
            fail(syncLog, startedAt, e);
            throw e;
        }
    }

    private int sync(VcsCredentials credentials, VcsRepoBinding binding, Instant startedAt, Instant backfillFrom) {
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
                credentials, client, authors, repository, workItemLinkConfig(orgId));

        int commits = contentSync.syncCommits(context, lastSyncAt != null ? lastSyncAt : backfillFrom);
        List<VcsPullRequest> pullRequests = contentSync.syncPullRequests(context, lastSyncAt, backfillFrom);
        int events = contentSync.syncReviewEvents(context, pullRequests, lastSyncAt);

        binding.setLastSyncAt(startedAt);
        bindingRepository.save(binding);
        int processed = 1 + commits + pullRequests.size() + events;
        log.info("VCS sync completed: org={} project={} provider={} repo={} processed={}", orgId,
                binding.getProjectId(), binding.getProvider(), repository.getDisplayName(), processed);
        return processed;
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
