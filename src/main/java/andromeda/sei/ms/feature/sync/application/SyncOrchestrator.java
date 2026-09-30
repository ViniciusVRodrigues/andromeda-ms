package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncStatusAggregator;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.sync.application.runner.BindingSyncBatch;
import andromeda.sei.ms.feature.sync.application.runner.BindingSyncRunner;
import andromeda.sei.ms.feature.sync.domain.BindingSyncOutcome;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.sync.domain.SyncTargetKind;
import andromeda.sei.ms.feature.sync.domain.exception.SyncCooldownActiveException;
import andromeda.sei.ms.feature.sync.domain.exception.SyncQueueFullException;
import andromeda.sei.ms.shared.infrastructure.config.AsyncExecutorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orquestrador unico de sync (design D1): todo caminho — refresh de projeto, trigger manual de
 * organizacao, sync por binding, repair e agendado — passa por aqui, entao cron e trigger manual
 * deixam de divergir.
 *
 * <p>Fluxo: admissao pelo cooldown por binding (sincrona, no thread do pedido) → log pai
 * ({@code PROJECT_REFRESH}/{@code ORGANIZATION_SYNC}) → um log filho {@code SYNC_BINDING} por
 * binding → runners por integracao (Azure antes do GitHub, para o vinculo PR → work item enxergar
 * os work items recem-sincronizados) → recalculo das metricas dos projetos afetados → status do
 * pai derivado dos filhos.
 */
@Slf4j
@Component
public class SyncOrchestrator {

    static final String INTERRUPTED = "SYNC_INTERRUPTED";
    static final String PROVIDER_UNSUPPORTED = "SYNC_PROVIDER_UNSUPPORTED";

    private final IntegrationSyncLogRepository syncLogRepository;
    private final SyncCooldownService cooldownService;
    private final Map<RunnerKey, BindingSyncRunner> runners;
    private final ProjectMetricsRecalculator projectMetricsRecalculator;
    private final OrganizationRepository organizationRepository;
    private final Executor syncExecutor;

    public SyncOrchestrator(IntegrationSyncLogRepository syncLogRepository,
                            SyncCooldownService cooldownService,
                            List<BindingSyncRunner> runners,
                            ProjectMetricsRecalculator projectMetricsRecalculator,
                            OrganizationRepository organizationRepository,
                            @Qualifier(AsyncExecutorConfig.SYNC_EXECUTOR) Executor syncExecutor) {
        this.syncLogRepository = syncLogRepository;
        this.cooldownService = cooldownService;
        this.runners = runners.stream().collect(Collectors.toMap(
                runner -> new RunnerKey(runner.integration(), runner.kind()), Function.identity()));
        this.projectMetricsRecalculator = projectMetricsRecalculator;
        this.organizationRepository = organizationRepository;
        this.syncExecutor = syncExecutor;
    }

    /**
     * Admite o pedido e agenda o sync no executor dedicado, respondendo antes de qualquer chamada
     * externa. Bindings em cooldown sao pulados; os demais seguem.
     *
     * @return id do log pai (o {@code syncId} devolvido ao cliente)
     * @throws SyncCooldownActiveException quando todos os bindings estao em cooldown
     * @throws SyncQueueFullException      quando a fila do executor esta cheia
     */
    public String submit(SyncRequest request) {
        List<SyncTarget> admitted = admitOrThrow(request.targets());
        IntegrationSyncLog parent = createParent(request);
        if (admitted.isEmpty()) {
            return finalizeParent(request.organizationId(), parent.getId()).getId();
        }
        try {
            syncExecutor.execute(() -> syncBindings(request.organizationId(), admitted, request.trigger(),
                    parent.getId(), request.resetWatermarks()));
        } catch (RejectedExecutionException e) {
            admitted.forEach(target -> cooldownService.release(target.bindingId()));
            failParent(parent, new SyncQueueFullException());
            throw new SyncQueueFullException();
        }
        return parent.getId();
    }

    /**
     * Executa o sync no thread atual (usado pelo job agendado, que ja roda em background).
     * Bindings em cooldown sao pulados em silencio — alguem acabou de sincroniza-los.
     *
     * @return status final do sync
     */
    public SyncStatus runNow(SyncRequest request) {
        List<SyncTarget> admitted = request.targets().stream()
                .filter(target -> cooldownService.tryAcquire(target.bindingId()) == 0)
                .toList();
        IntegrationSyncLog parent = createParent(request);
        return syncBindings(request.organizationId(), admitted, request.trigger(), parent.getId(),
                request.resetWatermarks());
    }

    /**
     * Sincroniza os bindings ja admitidos, atualiza a saude das conexoes (nos runners), recalcula
     * as metricas dos projetos afetados e fecha o log pai com o status derivado dos filhos.
     */
    public SyncStatus syncBindings(String organizationId, List<SyncTarget> targets, SyncTrigger trigger,
                                   String parentSyncId, boolean resetWatermarks) {
        try {
            Map<String, IntegrationSyncLog> children = createChildren(organizationId, targets, trigger, parentSyncId);
            Instant backfillFrom = resolveBackfillFrom(organizationId);
            List<BindingSyncOutcome> outcomes = new ArrayList<>();
            groupByRunner(targets).forEach((key, group) -> outcomes.addAll(
                    runGroup(new BindingSyncBatch(organizationId, group, trigger, backfillFrom, resetWatermarks),
                            key, children)));
            recalculateAffectedProjects(organizationId, outcomes);
        } catch (Exception e) {
            log.error("Sync orchestration failed for org={} parent={}", organizationId, parentSyncId, e);
        }
        return finalizeParent(organizationId, parentSyncId).getStatus();
    }

    private List<SyncTarget> admitOrThrow(List<SyncTarget> targets) {
        List<SyncTarget> admitted = new ArrayList<>();
        long retryAfter = Long.MAX_VALUE;
        for (SyncTarget target : targets) {
            long remaining = cooldownService.tryAcquire(target.bindingId());
            if (remaining == 0) {
                admitted.add(target);
            } else {
                retryAfter = Math.min(retryAfter, remaining);
            }
        }
        if (!targets.isEmpty() && admitted.isEmpty()) {
            throw new SyncCooldownActiveException(retryAfter);
        }
        return admitted;
    }

    private List<BindingSyncOutcome> runGroup(BindingSyncBatch batch, RunnerKey key,
                                              Map<String, IntegrationSyncLog> children) {
        List<BindingSyncOutcome> outcomes = new ArrayList<>();
        BindingSyncRunner runner = runners.get(key);
        Integration integration = key.integration();
        if (runner == null) {
            batch.targets().forEach(target -> outcomes.add(finishChild(children,
                    BindingSyncOutcome.failed(target, PROVIDER_UNSUPPORTED, "No sync runner for " + key))));
            return outcomes;
        }
        try {
            runner.run(batch, outcome -> outcomes.add(finishChild(children, outcome)));
        } catch (Exception e) {
            log.error("{} sync runner failed for org={}", integration, batch.organizationId(), e);
        }
        return outcomes;
    }

    private void recalculateAffectedProjects(String organizationId, List<BindingSyncOutcome> outcomes) {
        outcomes.stream()
                .filter(BindingSyncOutcome::reachedProvider)
                .map(outcome -> outcome.target().projectId())
                .filter(Objects::nonNull)
                .distinct()
                .forEach(projectId -> projectMetricsRecalculator.recalculate(organizationId, projectId));
    }

    private Instant resolveBackfillFrom(String organizationId) {
        SyncSettings settings = organizationRepository.findById(organizationId)
                .map(Organization::getSyncSettings)
                .orElseGet(SyncSettings::defaults);
        return settings.backfillFrom(Instant.now());
    }

    /**
     * Agrupa por runner na ordem Azure (Boards, depois Repos) e GitHub: o vinculo PR → work item
     * enxerga os work items recem-sincronizados.
     */
    private static Map<RunnerKey, List<SyncTarget>> groupByRunner(List<SyncTarget> targets) {
        Map<RunnerKey, List<SyncTarget>> grouped = new TreeMap<>(RunnerKey.ORDER);
        targets.forEach(target -> grouped.computeIfAbsent(new RunnerKey(target.integration(), target.kind()),
                k -> new ArrayList<>()).add(target));
        return grouped;
    }

    /** Chave de despacho: a integracao e o tipo de binding. */
    record RunnerKey(Integration integration, SyncTargetKind kind) {
        static final Comparator<RunnerKey> ORDER = Comparator.comparing(RunnerKey::integration)
                .thenComparing(RunnerKey::kind);
    }

    // ------------------------------------------------------------------
    // Logs pai/filho
    // ------------------------------------------------------------------

    private IntegrationSyncLog createParent(SyncRequest request) {
        IntegrationSyncLog parent = new IntegrationSyncLog(null, request.organizationId(), request.integration(),
                request.operation(), SyncStatus.RUNNING, request.trigger(), request.projectId(), Instant.now(),
                null, null, 0, 0, null, null);
        return syncLogRepository.save(parent);
    }

    private Map<String, IntegrationSyncLog> createChildren(String organizationId, List<SyncTarget> targets,
                                                           SyncTrigger trigger, String parentSyncId) {
        Map<String, IntegrationSyncLog> children = new LinkedHashMap<>();
        for (SyncTarget target : targets) {
            IntegrationSyncLog child = new IntegrationSyncLog(null, organizationId, target.integration(),
                    SyncOperation.SYNC_BINDING, SyncStatus.RUNNING, trigger, target.projectId(), Instant.now(),
                    null, null, 0, 0, null, null);
            child.setParentSyncId(parentSyncId);
            child.setBindingId(target.bindingId());
            children.put(target.bindingId(), syncLogRepository.save(child));
        }
        return children;
    }

    private BindingSyncOutcome finishChild(Map<String, IntegrationSyncLog> children, BindingSyncOutcome outcome) {
        IntegrationSyncLog child = children.get(outcome.target().bindingId());
        if (child != null) {
            complete(child, outcome.status(), outcome.errorCode(), outcome.errorMessage());
            child.setItemsProcessed(outcome.itemsProcessed());
            child.setItemsFailed(outcome.itemsFailed());
            children.put(outcome.target().bindingId(), syncLogRepository.save(child));
        }
        return outcome;
    }

    /**
     * Fecha o pai com o status derivado dos filhos. Filho ainda {@code RUNNING} a esta altura
     * (runner abortado no meio) e encerrado como {@code FAILED}: o pai nunca fica preso rodando.
     */
    private IntegrationSyncLog finalizeParent(String organizationId, String parentSyncId) {
        List<IntegrationSyncLog> children = syncLogRepository
                .findAllByOrganizationIdAndParentSyncId(organizationId, parentSyncId);
        Map<String, SyncStatus> statuses = new HashMap<>();
        for (IntegrationSyncLog child : children) {
            if (child.isRunning()) {
                complete(child, SyncStatus.FAILED, INTERRUPTED, "Binding sync did not finish");
                syncLogRepository.save(child);
            }
            statuses.put(child.getId(), child.getStatus());
        }
        IntegrationSyncLog parent = syncLogRepository.findByIdAndOrganizationId(parentSyncId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Parent sync log vanished: " + parentSyncId));
        complete(parent, SyncStatusAggregator.aggregate(statuses.values()), null, null);
        parent.setItemsProcessed(children.stream().mapToInt(c -> nullToZero(c.getItemsProcessed())).sum());
        parent.setItemsFailed(children.stream().mapToInt(c -> nullToZero(c.getItemsFailed())).sum());
        return syncLogRepository.save(parent);
    }

    private void failParent(IntegrationSyncLog parent, SyncQueueFullException reason) {
        complete(parent, SyncStatus.FAILED, reason.getCode(), reason.getMessage());
        syncLogRepository.save(parent);
    }

    private static void complete(IntegrationSyncLog log, SyncStatus status, String errorCode, String errorMessage) {
        Instant finishedAt = Instant.now();
        log.setStatus(status);
        log.setFinishedAt(finishedAt);
        log.setDurationMs(finishedAt.toEpochMilli() - log.getStartedAt().toEpochMilli());
        log.setErrorCode(errorCode);
        log.setErrorMessage(errorMessage);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
