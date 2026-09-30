package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.ConnectionHealthUpdater;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.vcs.application.VcsIntegrations;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.usecase.SyncRepositoryUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException;
import andromeda.sei.ms.feature.sync.domain.SyncTargetKind;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sync of repository bindings (vcs-core D5), one at a time — the providers' secondary rate limits
 * are triggered by concurrency. Everything provider-specific goes through the ports resolved by
 * {@link VcsProviderRegistry}; the sync itself is {@link SyncRepositoryUseCase}.
 *
 * <p>One instance per provider ({@code VcsSyncRunnerConfig}): GitHub under {@link Integration#GITHUB},
 * Azure Repos under {@link Integration#AZURE_DEVOPS} next to the Boards runner, told apart by
 * {@link SyncTargetKind#REPOSITORY}.
 *
 * <p>Connection health: a binding denied for missing code scope ({@code AZURE_REPOS_SCOPE_MISSING})
 * does not count as a connection failure — the Azure DevOps connection is shared with Boards, and
 * three such runs would otherwise flip it to {@code ERROR} and stop the Boards cron (D6).
 */
@Slf4j
public class VcsBindingSyncRunner implements BindingSyncRunner {

    static final String CONNECTION_NOT_FOUND = "VCS_CONNECTION_NOT_FOUND";
    static final String CONNECTION_IN_ERROR = "VCS_CONNECTION_ERROR";
    static final String BINDING_NOT_FOUND = "REPO_BINDING_NOT_FOUND";
    static final String SYNC_FAILED = "VCS_SYNC_FAILED";
    /** Denials that prove the connection works but lacks a scope only this provider needs. */
    static final Set<String> SCOPE_ONLY_FAILURES = Set.of(AzureReposScopeMissingException.CODE);

    private final VcsProvider provider;
    private final VcsProviderRegistry providerRegistry;
    private final VcsRepoBindingRepository bindingRepository;
    private final SyncRepositoryUseCase syncRepositoryUseCase;
    private final ConnectionHealthUpdater connectionHealthUpdater;

    public VcsBindingSyncRunner(VcsProvider provider, VcsProviderRegistry providerRegistry,
                                VcsRepoBindingRepository bindingRepository, SyncRepositoryUseCase syncRepositoryUseCase,
                                ConnectionHealthUpdater connectionHealthUpdater) {
        this.provider = provider;
        this.providerRegistry = providerRegistry;
        this.bindingRepository = bindingRepository;
        this.syncRepositoryUseCase = syncRepositoryUseCase;
        this.connectionHealthUpdater = connectionHealthUpdater;
    }

    @Override
    public Integration integration() {
        return VcsIntegrations.integrationOf(provider);
    }

    @Override
    public SyncTargetKind kind() {
        return SyncTargetKind.REPOSITORY;
    }

    @Override
    public void run(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome) {
        String orgId = batch.organizationId();
        Optional<VcsCredentials> found;
        try {
            found = providerRegistry.credentialsProvider(provider).credentials(orgId);
        } catch (RuntimeException e) {
            /* e.g. the Azure DevOps OAuth token could not be refreshed */
            log.error("{} credentials unavailable for org={}", provider, orgId, e);
            failAll(batch, onOutcome, SyncFailures.codeOf(e, CONNECTION_NOT_FOUND), SyncFailures.messageOf(e));
            connectionHealthUpdater.recordVcs(provider, orgId, false);
            return;
        }
        if (found.isEmpty()) {
            failAll(batch, onOutcome, CONNECTION_NOT_FOUND, "No active " + provider + " connection");
            return;
        }
        VcsCredentials credentials = found.get();
        /* The cron skips connections in ERROR; a manual refresh does not — it is the recovery path. */
        if (batch.trigger() == SyncTrigger.SCHEDULED && credentials.inError()) {
            failAll(batch, onOutcome, CONNECTION_IN_ERROR, provider + " connection is in ERROR");
            return;
        }

        boolean anyFailed = false;
        for (SyncTarget target : batch.targets()) {
            BindingSyncOutcome outcome = syncBinding(credentials, target, batch);
            anyFailed |= countsAgainstConnection(outcome);
            onOutcome.accept(outcome);
        }
        connectionHealthUpdater.recordVcs(provider, orgId, !anyFailed);
    }

    static boolean countsAgainstConnection(BindingSyncOutcome outcome) {
        return !outcome.reachedProvider() && !SCOPE_ONLY_FAILURES.contains(outcome.errorCode());
    }

    private BindingSyncOutcome syncBinding(VcsCredentials credentials, SyncTarget target, BindingSyncBatch batch) {
        String orgId = batch.organizationId();
        try {
            Optional<VcsRepoBinding> found = bindingRepository.findByIdAndOrganizationIdAndActiveTrue(
                    target.bindingId(), orgId);
            if (found.isEmpty()) {
                return BindingSyncOutcome.failed(target, BINDING_NOT_FOUND, "Binding not found: " + target.bindingId());
            }
            VcsRepoBinding binding = found.get();
            if (batch.resetWatermarks()) {
                binding.setLastSyncAt(null);
                binding = bindingRepository.save(binding);
            }
            int processed = syncRepositoryUseCase.execute(credentials, binding, batch.trigger(), batch.backfillFrom());
            return BindingSyncOutcome.success(target, processed);
        } catch (Exception e) {
            log.error("VCS sync failed for org={} project={} binding={}", orgId, target.projectId(), target.bindingId(), e);
            return BindingSyncOutcome.failed(target, SyncFailures.codeOf(e, SYNC_FAILED), SyncFailures.messageOf(e));
        }
    }

    private static void failAll(BindingSyncBatch batch, Consumer<BindingSyncOutcome> onOutcome,
                                String errorCode, String message) {
        batch.targets().forEach(target -> onOutcome.accept(BindingSyncOutcome.failed(target, errorCode, message)));
    }
}
