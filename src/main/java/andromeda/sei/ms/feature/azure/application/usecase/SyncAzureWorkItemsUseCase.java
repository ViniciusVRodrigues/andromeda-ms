package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.DefaultWorkflowConfigFactory;
import andromeda.sei.ms.feature.azure.application.dto.AzureWorkItemsSyncResult;
import andromeda.sei.ms.feature.azure.application.port.AzureOrgUserCollector;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureSyncPageLimitExceededException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemUpdateDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncAzureWorkItemsUseCase {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final AzureAuthStrategyResolver authStrategyResolver;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTransitionRepository workItemTransitionRepository;
    private final SprintRepository sprintRepository;
    private final AzureDevOpsClient azureDevOpsClient;
    private final UserRepository userRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final AzureOrgUserCollector orgUserCollector;

    /**
     * @return o binding tal como persistido, com a {@code version} atualizada. Chamadores
     *         que encadeiam syncs sobre o mesmo binding devem seguir com esta instância:
     *         reutilizar a original (versão já consumida por este save) faz o save seguinte
     *         falhar com OptimisticLockingFailureException.
     */
    public AzureProjectBinding execute(AzureConnection connection, AzureProjectBinding binding, SyncTrigger trigger) {
        return sync(connection, binding, trigger, null).binding();
    }

    /**
     * Sincroniza os work items do binding e devolve o desfecho completo.
     *
     * @param backfillFrom limite inferior do primeiro sync (quando o binding ainda nao tem
     *                     watermark): so work items alterados a partir dele sao buscados, via
     *                     {@code [System.ChangedDate] >= backfillFrom} no WIQL. {@code null}
     *                     busca o historico inteiro. Ignorado nos syncs incrementais.
     * @return binding persistido e status; {@code PARTIAL} quando algum item falhou
     */
    public AzureWorkItemsSyncResult sync(AzureConnection connection, AzureProjectBinding binding,
                                         SyncTrigger trigger, Instant backfillFrom) {
        Instant startedAt = Instant.now();
        IntegrationSyncLog syncLog = new IntegrationSyncLog(
                null, connection.getOrganizationId(), Integration.AZURE_DEVOPS,
                SyncOperation.SYNC_WORK_ITEMS, SyncStatus.RUNNING, trigger,
                binding.getProjectId(), startedAt, null, null, 0, 0, null, null);
        syncLog = syncLogRepository.save(syncLog);

        int processed = 0;
        int failed = 0;

        try {
            String orgUrl = connection.getAzureOrgUrl();
            String authHeader = authStrategyResolver.resolve(connection);
            String orgId = connection.getOrganizationId();
            String projectId = binding.getProjectId();
            String azureProjectId = binding.getAzureProjectId();

            WorkflowConfig config = binding.getWorkflowConfig() != null
                    ? binding.getWorkflowConfig()
                    : DefaultWorkflowConfigFactory.fallback();

            /* Marcador PROPRIO do sync de work items. Usar lastSyncAt aqui era o bug:
             * o sync de sprints roda antes e o carimba com now(), entao um binding
             * novo nunca via null e nascia incremental com since = hoje — importando
             * so o que mudou no dia. */
            Instant watermark = binding.getWorkItemsWatermark();
            boolean fullSync = watermark == null;
            /* Primeiro sync limitado pela janela de backfill da organizacao (design D6): sem
             * isso um projeto com anos de historico baixaria tudo de uma vez. */
            Instant since = fullSync ? backfillFrom : watermark;
            log.info("Work item sync for org={} project={} mode={} since={}", orgId, projectId,
                    fullSync ? "FULL" : "INCREMENTAL", since);

            List<Integer> ids = fetchAllWorkItemIds(orgUrl, azureProjectId, authHeader,
                    since, config);

            if (ids.isEmpty()) {
                log.info("No work items to sync for org={} project={}", orgId, projectId);
                finishSuccess(syncLog, startedAt, 0, 0);
                binding.setLastSyncAt(Instant.now());
                binding.setWorkItemsWatermark(startedAt);
                return new AzureWorkItemsSyncResult(
                        azureProjectBindingRepository.save(binding), SyncStatus.SUCCESS, 0, 0);
            }

            List<AdoWorkItemDto> adoItems = azureDevOpsClient.getWorkItemsBatch(orgUrl, ids, authHeader);

            Map<String, AzureOrgUserCollector.ObservedIdentity> seen = new java.util.LinkedHashMap<>();
            for (AdoWorkItemDto adoItem : adoItems) {
                try {
                    processWorkItem(orgId, projectId, adoItem, config, orgUrl, authHeader);
                    collectIdentities(adoItem, seen);
                    processed++;
                } catch (Exception e) {
                    log.warn("Failed to process work item id={}: {}", adoItem.id(), e.getMessage());
                    failed++;
                }
            }

            collectSeenIdentities(orgId, seen);

            binding.setLastSyncAt(Instant.now());
            /* startedAt, nao now(): item editado DURANTE o sync tem ChangedDate menor
             * que o instante final e cairia abaixo do watermark para sempre. */
            binding.setWorkItemsWatermark(startedAt);
            AzureProjectBinding saved = azureProjectBindingRepository.save(binding);

            Instant finishedAt = Instant.now();
            SyncStatus status = resolveStatus(processed, failed);
            syncLog.setStatus(status);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(failed);
            syncLogRepository.save(syncLog);
            log.info("Work item sync completed: org={} project={} status={} processed={} failed={} durationMs={}",
                    connection.getOrganizationId(), binding.getProjectId(), status, processed, failed,
                    finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            return new AzureWorkItemsSyncResult(saved, status, processed, failed);

        } catch (Exception e) {
            Instant finishedAt = Instant.now();
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setFinishedAt(finishedAt);
            syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
            syncLog.setItemsProcessed(processed);
            syncLog.setItemsFailed(failed);
            if (e instanceof AzureSyncPageLimitExceededException pageLimitEx) {
                syncLog.setErrorCode(pageLimitEx.getCode());
            } else {
                syncLog.setErrorCode("AZURE_SYNC_WORK_ITEMS_FAILED");
            }
            syncLog.setErrorMessage(e.getMessage());
            // When Reactor exhausts its retries it wraps the original response
            // exception, which is where the Azure DevOps error body lives.
            Throwable root = e;
            while (!(root instanceof WebClientResponseException) && root.getCause() != null
                    && root.getCause() != root) {
                root = root.getCause();
            }
            if (root instanceof WebClientResponseException wex) {
                syncLog.setHttpStatus(wex.getStatusCode().value());
                String body = wex.getResponseBodyAsString();
                syncLog.setErrorDetail(body.length() > 2000 ? body.substring(0, 2000) : body);
                log.error("Azure API HTTP error during work item sync: status={} body={}",
                        wex.getStatusCode().value(), syncLog.getErrorDetail());
            }
            syncLogRepository.save(syncLog);
            throw e;
        }
    }

    /**
     * Um sync so e {@code SUCCESS} quando nenhum item falhou. Reportar sucesso com itens
     * perdidos escondia do refresh (e do usuario) que o historico ficou incompleto.
     */
    private static SyncStatus resolveStatus(int processed, int failed) {
        if (failed == 0) return SyncStatus.SUCCESS;
        return processed > 0 ? SyncStatus.PARTIAL : SyncStatus.FAILED;
    }

    /**
     * Fetches all matching work item IDs by looping over WIQL pages ordered ascending by
     * System.Id, until a page returns fewer than the page size (last page) or the hard
     * page-count safety cap is reached (in which case the sync fails rather than looping
     * forever or silently truncating results).
     */
    private List<Integer> fetchAllWorkItemIds(String orgUrl, String azureProjectId, String authHeader,
                                               Instant since, WorkflowConfig config) {
        List<Integer> allIds = new ArrayList<>();
        Integer lastSeenId = null;
        int pageCount = 0;

        while (true) {
            if (pageCount >= AzureDevOpsClient.WIQL_MAX_PAGES) {
                throw new AzureSyncPageLimitExceededException(AzureDevOpsClient.WIQL_MAX_PAGES);
            }

            List<Integer> page = azureDevOpsClient.queryWorkItemIdsPage(
                    orgUrl, azureProjectId, authHeader, since, lastSeenId, config.getSyncFilter());
            pageCount++;

            if (page.isEmpty()) break;

            allIds.addAll(page);
            lastSeenId = page.get(page.size() - 1);

            if (page.size() < AzureDevOpsClient.WIQL_PAGE_SIZE) break;
        }

        return allIds;
    }

    private void processWorkItem(String orgId, String projectId, AdoWorkItemDto adoItem,
                                  WorkflowConfig config, String orgUrl, String authHeader) {
        Map<String, Object> fields = adoItem.fields() != null ? adoItem.fields() : Map.of();
        String externalId = String.valueOf(adoItem.id());

        Optional<WorkItem> existing = workItemRepository.findByOrganizationIdAndExternalIdAndSource(
                orgId, externalId, WorkItem.Source.AZURE_DEVOPS);
        // Capture stored status before any mutations for incremental transition comparison
        String storedRawStatus = existing.map(WorkItem::getRawStatus).orElse(null);

        String rawType = getString(fields, "System.WorkItemType");
        String rawStatus = getString(fields, "System.State");
        WorkItem.ResolvedType resolvedType = resolveType(rawType, config);
        WorkItem.ResolvedStatus resolvedStatus = resolveStatus(rawType, rawStatus, config);

        String iterationPath = getString(fields, "System.IterationPath");
        Sprint sprint = resolveSprint(orgId, projectId, iterationPath);

        String tagsRaw = getString(fields, "System.Tags");
        List<String> labels = tagsRaw != null
                ? List.of(tagsRaw.split(";")).stream().map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of();

        Instant now = Instant.now();

        WorkItem workItem = existing.orElseGet(() -> new WorkItem(
                null, orgId, projectId, WorkItem.Source.AZURE_DEVOPS,
                externalId, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, true, null, now,
                null, null, null, null, null, null, null, null));

        workItem.setProjectId(projectId);
        workItem.setExternalUpdatedAt(parseInstant(fields, "System.ChangedDate"));
        workItem.setTitle(getString(fields, "System.Title"));
        workItem.setRawType(rawType);
        workItem.setResolvedType(resolvedType);
        workItem.setRawStatus(rawStatus);
        workItem.setResolvedStatus(resolvedStatus);
        workItem.setPriority(getString(fields, "Microsoft.VSTS.Common.Priority"));
        applyComplexityAndEffort(workItem, fields, config, rawType);
        // Dual-write: keep legacy storyPoints in sync with effort for this release
        // (see design Migration Plan step 2) -- dashboards still read storyPoints until cutover.
        workItem.setStoryPoints(workItem.getEffort());
        workItem.setLabels(labels);
        workItem.setSprintId(sprint != null ? sprint.getId() : null);
        workItem.setSprintName(sprint != null ? sprint.getName() : null);
        workItem.setAssigneeExternalId(getAssigneeId(fields));
        workItem.setAssigneeEmail(getAssigneeEmail(fields));
        workItem.setParentExternalId(extractParentExternalId(adoItem));
        resolveAssigneeUserId(orgId, workItem);
        if (workItem.getCreatedAt() == null) {
            workItem.setCreatedAt(parseInstant(fields, "System.CreatedDate"));
        }

        // Seed startedAt / completedAt directly from ADO fields -- the transitions
        // history may be incomplete or unavailable, so these are authoritative fallbacks.
        Instant adoActivatedDate = parseInstant(fields, "Microsoft.VSTS.Common.ActivatedDate");
        Instant adoClosedDate = firstNonNull(
                parseInstant(fields, "Microsoft.VSTS.Common.ClosedDate"),
                parseInstant(fields, "Microsoft.VSTS.Common.ResolvedDate"));

        if (adoActivatedDate != null) workItem.setStartedAt(adoActivatedDate);
        if (adoClosedDate != null) workItem.setCompletedAt(adoClosedDate);

        // Save first to ensure workItemId is set before building transitions
        WorkItem saved = workItemRepository.save(workItem);

        buildTransitions(saved, storedRawStatus, adoItem, config, orgUrl, authHeader, rawType);

        // Re-derive from transitions -- prefer transition-derived timestamps (more granular)
        // but fall back to the ADO field values already set above if transitions give nothing.
        List<WorkItemTransition> transitions = workItemTransitionRepository.findAllByWorkItemId(saved.getId());
        Instant transitionStartedAt = deriveStartedAt(transitions);
        Instant transitionCompletedAt = deriveCompletedAt(transitions);
        if (transitionStartedAt != null) saved.setStartedAt(transitionStartedAt);
        if (transitionCompletedAt != null) saved.setCompletedAt(transitionCompletedAt);
        workItemRepository.save(saved);
    }

    /**
     * Applies complexity/effort from the mapped ADO fields, honoring manual overrides:
     * a field whose current source is MANUAL_OVERRIDE is left untouched by this sync run.
     */
    private void applyComplexityAndEffort(WorkItem workItem, Map<String, Object> fields, WorkflowConfig config,
                                           String rawType) {
        if (workItem.getComplexitySource() != FieldValueSource.MANUAL_OVERRIDE) {
            Double complexity = config.resolveFieldMapping(FieldMapping.InternalFieldTarget.COMPLEXITY, rawType)
                    .map(m -> getDouble(fields, m.getAzureFieldReferenceName()))
                    .orElse(null);
            workItem.setComplexity(complexity);
            workItem.setComplexitySource(FieldValueSource.AZURE_SYNC);
        }

        if (workItem.getEffortSource() != FieldValueSource.MANUAL_OVERRIDE) {
            Double effort = config.resolveFieldMapping(FieldMapping.InternalFieldTarget.EFFORT, rawType)
                    .map(m -> getDouble(fields, m.getAzureFieldReferenceName()))
                    .orElse(null);
            workItem.setEffort(effort);
            workItem.setEffortSource(FieldValueSource.AZURE_SYNC);
        }
    }

    /**
     * Re-fetches the given work items from Azure DevOps (batched) and updates only their
     * {@code parentExternalId} from the current {@code System.LinkTypes.Hierarchy-Reverse}
     * relation, saving each item whose parent link changed. Used by
     * {@code WorkItemParentLinkBackfillMigration} to repair documents saved before
     * WorkItemRepositoryImpl.save() persisted parentExternalId, and safe to re-run any number of
     * times — it always reflects Azure's current state and is a no-op for items whose stored
     * value already matches.
     *
     * @return the number of items whose stored parentExternalId was changed
     */
    public int backfillParentLinks(List<WorkItem> items, String orgUrl, String authHeader) {
        if (items.isEmpty()) return 0;

        Map<String, WorkItem> byExternalId = new java.util.HashMap<>();
        List<Integer> ids = new ArrayList<>();
        for (WorkItem item : items) {
            byExternalId.put(item.getExternalId(), item);
            try {
                ids.add(Integer.parseInt(item.getExternalId()));
            } catch (NumberFormatException e) {
                log.warn("backfillParentLinks: invalid externalId={} for workItemId={}",
                        item.getExternalId(), item.getId());
            }
        }
        if (ids.isEmpty()) return 0;

        List<AdoWorkItemDto> adoItems = azureDevOpsClient.getWorkItemsBatch(orgUrl, ids, authHeader);

        int updated = 0;
        for (AdoWorkItemDto adoItem : adoItems) {
            WorkItem item = byExternalId.get(String.valueOf(adoItem.id()));
            if (item == null) continue;

            String parentExternalId = extractParentExternalId(adoItem);
            if (java.util.Objects.equals(parentExternalId, item.getParentExternalId())) continue;

            item.setParentExternalId(parentExternalId);
            workItemRepository.save(item);
            updated++;
        }
        return updated;
    }

    /**
     * Rebuilds the complete transition history for a work item from the Azure DevOps updates API.
     * Clears existing transitions and rebuilds from full ADO history.
     * This method is reusable by both the initial full-sync path and ForceResyncProjectUseCase.
     */
    public void rebuildTransitionHistory(WorkItem item, WorkflowConfig config, String orgUrl, String authHeader) {
        String workItemId = item.getId();
        if (workItemId == null) return;

        WorkflowConfig effectiveConfig = config != null ? config : DefaultWorkflowConfigFactory.fallback();

        buildFullTransitionHistoryById(workItemId, item.getOrganizationId(), item.getExternalId(),
                effectiveConfig, orgUrl, authHeader, item.getRawStatus(), item.getCreatedAt(), item.getRawType());

        List<WorkItemTransition> transitions = workItemTransitionRepository.findAllByWorkItemId(workItemId);
        item.setStartedAt(deriveStartedAt(transitions));
        item.setCompletedAt(deriveCompletedAt(transitions));
        workItemRepository.save(item);
    }

    private void buildTransitions(WorkItem workItem, String storedRawStatus, AdoWorkItemDto adoItem,
                                   WorkflowConfig config, String orgUrl, String authHeader, String rawType) {
        String workItemId = workItem.getId();
        if (workItemId == null) return;

        boolean hasTransitions = workItemTransitionRepository.existsByWorkItemId(workItemId);

        if (!hasTransitions) {
            buildFullTransitionHistoryById(workItemId, workItem.getOrganizationId(),
                    String.valueOf(adoItem.id()), config, orgUrl, authHeader,
                    workItem.getRawStatus(), workItem.getCreatedAt(), rawType);
        } else {
            appendIncrementalTransition(workItemId, workItem.getOrganizationId(),
                    storedRawStatus, adoItem, config, rawType);
        }
    }

    private void buildFullTransitionHistoryById(String workItemId, String orgId, String externalId,
                                                 WorkflowConfig config, String orgUrl, String authHeader,
                                                 String currentRawStatus, Instant createdAt, String rawType) {
        int adoItemId;
        try {
            adoItemId = Integer.parseInt(externalId);
        } catch (NumberFormatException e) {
            log.warn("rebuildTransitionHistory: invalid externalId={} for workItemId={}", externalId, workItemId);
            return;
        }
        List<AdoWorkItemUpdateDto> updates = azureDevOpsClient.getWorkItemUpdates(orgUrl, adoItemId, authHeader);

        List<WorkItemTransition> transitions = new ArrayList<>();
        String prevStatus = null;

        for (AdoWorkItemUpdateDto update : updates) {
            if (update.fields() == null) continue;
            Object stateChange = update.fields().get("System.State");
            if (stateChange == null) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> stateField = (Map<String, Object>) stateChange;
            String newVal = getString(stateField, "newValue");
            if (newVal == null) continue;

            // revisedDate is ADO's "valid-until" sentinel (often 9999-01-01); use System.ChangedDate instead.
            Instant occurredAt = getChangedDateFromUpdate(update);
            if (occurredAt == null) occurredAt = Instant.now();

            WorkItem.ResolvedStatus resolved = resolveStatus(rawType, newVal, config);
            transitions.add(new WorkItemTransition(
                    null, orgId, workItemId, prevStatus, newVal, resolved,
                    occurredAt, Instant.now(), true, null, Instant.now(), Instant.now()));
            prevStatus = newVal;
        }

        if (transitions.isEmpty()) {
            WorkItem.ResolvedStatus resolved = resolveStatus(rawType, currentRawStatus, config);
            transitions.add(new WorkItemTransition(
                    null, orgId, workItemId, null, currentRawStatus, resolved,
                    createdAt != null ? createdAt : Instant.now(),
                    Instant.now(), true, null, Instant.now(), Instant.now()));
        }

        for (WorkItemTransition t : transitions) {
            workItemTransitionRepository.upsertByCompositeKey(t);
        }
    }

    private void appendIncrementalTransition(String workItemId, String orgId,
                                              String storedRawStatus, AdoWorkItemDto adoItem,
                                              WorkflowConfig config, String rawType) {
        if (storedRawStatus == null) return;
        Map<String, Object> fields = adoItem.fields() != null ? adoItem.fields() : Map.of();
        String incomingStatus = getString(fields, "System.State");
        String storedStatus = storedRawStatus;

        if (incomingStatus == null || incomingStatus.equals(storedStatus)) return;

        Instant occurredAt = parseInstant(fields, "System.ChangedDate");
        if (occurredAt == null) occurredAt = Instant.now();

        WorkItem.ResolvedStatus resolved = resolveStatus(rawType, incomingStatus, config);
        WorkItemTransition transition = new WorkItemTransition(
                null, orgId, workItemId, storedStatus, incomingStatus, resolved,
                occurredAt, Instant.now(), true, null, Instant.now(), Instant.now());
        workItemTransitionRepository.upsertByCompositeKey(transition);
    }

    private Instant deriveStartedAt(List<WorkItemTransition> transitions) {
        return transitions.stream()
                .filter(t -> t.getToResolvedStatus() == WorkItem.ResolvedStatus.IN_PROGRESS)
                .map(WorkItemTransition::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private Instant deriveCompletedAt(List<WorkItemTransition> transitions) {
        return transitions.stream()
                .filter(t -> t.getToResolvedStatus() == WorkItem.ResolvedStatus.DONE)
                .map(WorkItemTransition::getOccurredAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private WorkItem.ResolvedType resolveType(String rawType, WorkflowConfig config) {
        if (rawType == null || config == null || config.getTypeMappings() == null) {
            return WorkItem.ResolvedType.TASK;
        }
        return config.getTypeMappings().stream()
                .filter(m -> rawType.equalsIgnoreCase(m.getRawType()))
                .map(m -> m.getResolvedType())
                .findFirst()
                .orElse(WorkItem.ResolvedType.TASK);
    }

    /**
     * Resolves the status for a work item by matching both {@code workItemType} and
     * {@code rawStatus}. A {@link StatusMapping} entry whose {@code workItemType} is {@code null}
     * is a legacy record (predating per-type status mapping) and is treated as matching any
     * work item type.
     */
    private WorkItem.ResolvedStatus resolveStatus(String rawType, String rawStatus, WorkflowConfig config) {
        if (rawStatus == null || config == null || config.getStatusMappings() == null) {
            return WorkItem.ResolvedStatus.TODO;
        }
        return config.getStatusMappings().stream()
                .filter(m -> rawStatus.equalsIgnoreCase(m.getRawStatus()))
                .filter(m -> m.getWorkItemType() == null
                        || (rawType != null && rawType.equalsIgnoreCase(m.getWorkItemType())))
                .map(StatusMapping::getResolvedStatus)
                .findFirst()
                .orElse(WorkItem.ResolvedStatus.TODO);
    }

    /**
     * Resolve a sprint do item pelo iteration path, devolvendo id e nome juntos. O nome é
     * desnormalizado em {@code WorkItem.sprintName} para que a listagem do backlog possa
     * exibir e filtrar por sprint sem um join por página — e sai de graça aqui, já que
     * esta busca sempre carregou o documento inteiro e descartava tudo menos o id.
     *
     * <p>Uma iteração compartilhada por vários times existe uma vez por time (sprints são únicas por
     * {@code (organizationId, externalId, teamId)}): a busca devolve todas as "gêmeas" do projeto e
     * escolhe a canônica ({@link SharedIterations}) — a mesma que dashboards e métricas leem. A
     * busca antiga por path (resultado único) lançava {@code IncorrectResultSizeDataAccessException},
     * e o item contava como falha e ficava sem sprint.
     */
    private Sprint resolveSprint(String orgId, String projectId, String iterationPath) {
        if (iterationPath == null) return null;
        return SharedIterations.canonical(
                sprintRepository.findAllByOrganizationIdAndProjectIdAndPath(orgId, projectId, iterationPath))
                .orElse(null);
    }

    private String getString(Map<String, Object> fields, String key) {
        Object val = fields.get(key);
        return val != null ? val.toString() : null;
    }

    private Double getDouble(Map<String, Object> fields, String key) {
        if (key == null) return null;
        Object val = fields.get(key);
        if (val == null) return null;
        try { return ((Number) val).doubleValue(); } catch (Exception e) { return null; }
    }

    private static final Instant ADO_SENTINEL = Instant.parse("9998-01-01T00:00:00Z");

    private Instant parseInstant(Map<String, Object> fields, String key) {
        String val = getString(fields, key);
        if (val == null) return null;
        try {
            Instant instant = Instant.parse(val);
            // ADO uses 9999-01-01 as "not set" sentinel -- treat as null
            return instant.isAfter(ADO_SENTINEL) ? null : instant;
        } catch (Exception e) { return null; }
    }

    // Azure DevOps has no scalar "parent" field -- the parent link (if any) is expressed as a
    // System.LinkTypes.Hierarchy-Reverse relation whose url ends in the parent's work item id
    // (e.g. .../_apis/wit/workItems/1234). A work item has at most one parent, so at most one
    // such relation is ever present.
    private String extractParentExternalId(AdoWorkItemDto adoItem) {
        List<AdoWorkItemDto.AdoWorkItemRelationDto> relations = adoItem.relations();
        if (relations == null) return null;
        for (AdoWorkItemDto.AdoWorkItemRelationDto relation : relations) {
            if ("System.LinkTypes.Hierarchy-Reverse".equals(relation.rel()) && relation.url() != null) {
                int lastSlash = relation.url().lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < relation.url().length() - 1) {
                    return relation.url().substring(lastSlash + 1);
                }
            }
        }
        return null;
    }

    private static final List<String> IDENTITY_FIELDS =
            List.of("System.AssignedTo", "System.CreatedBy", "System.ChangedBy");

    private static void collectIdentities(AdoWorkItemDto adoItem,
                                          Map<String, AzureOrgUserCollector.ObservedIdentity> seen) {
        if (adoItem.fields() == null) return;
        for (String field : IDENTITY_FIELDS) {
            if (!(adoItem.fields().get(field) instanceof Map<?, ?> identity)) continue;
            Object id = identity.get("id");
            if (id == null || Boolean.TRUE.equals(identity.get("isContainer"))) continue;
            Object name = identity.get("displayName");
            Object unique = identity.get("uniqueName");
            seen.putIfAbsent(id.toString(), new AzureOrgUserCollector.ObservedIdentity(id.toString(),
                    name != null ? name.toString() : null, unique != null ? unique.toString() : null,
                    AzureOrgUserSource.WORKITEM));
        }
    }

    /** One batch per sync; a failure here must never fail the work item sync. */
    private void collectSeenIdentities(String orgId, Map<String, AzureOrgUserCollector.ObservedIdentity> seen) {
        if (seen.isEmpty()) return;
        try {
            orgUserCollector.collect(orgId, seen.values());
        } catch (Exception e) {
            log.warn("Could not register identities seen in work items: org={}: {}", orgId, e.getMessage());
        }
    }

    private String getAssigneeId(Map<String, Object> fields) {
        Object val = fields.get("System.AssignedTo");
        if (val == null) return null;
        if (val instanceof Map<?, ?> identity) {
            Object id = identity.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private String getAssigneeEmail(Map<String, Object> fields) {
        Object val = fields.get("System.AssignedTo");
        if (val == null) return null;
        if (val instanceof Map<?, ?> identity) {
            Object uniqueName = identity.get("uniqueName");
            return uniqueName != null ? uniqueName.toString() : null;
        }
        return null;
    }

    private void resolveAssigneeUserId(String orgId, WorkItem workItem) {
        String externalId = workItem.getAssigneeExternalId();
        String email = workItem.getAssigneeEmail();
        // Azure is the source of truth: a reassignment to someone without an Andromeda
        // user (or no assignee) must not keep the previous user.
        workItem.setAssigneeUserId(null);
        if (externalId != null) {
            userRepository.findByOrganizationIdAndAzureId(orgId, externalId)
                    .ifPresent(user -> workItem.setAssigneeUserId(user.getId()));
        }
        if (workItem.getAssigneeUserId() == null && email != null && !email.isBlank()) {
            userRepository.findAllByOrganizationIdAndEmailIgnoringCase(orgId, email.trim()).stream()
                    .findFirst()
                    .ifPresent(user -> workItem.setAssigneeUserId(user.getId()));
        }
    }

    private Instant getChangedDateFromUpdate(AdoWorkItemUpdateDto update) {
        if (update.fields() == null) return null;
        Object changedDate = update.fields().get("System.ChangedDate");
        if (changedDate == null) return null;
        // The field value is a map with "newValue" key in the updates API
        if (changedDate instanceof Map<?, ?> m) {
            Object newVal = m.get("newValue");
            if (newVal == null) newVal = m.get("oldValue");
            if (newVal != null) {
                try { return Instant.parse(newVal.toString()); } catch (Exception ignored) {}
            }
        }
        try { return Instant.parse(changedDate.toString()); } catch (Exception ignored) {}
        return null;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T v : values) { if (v != null) return v; }
        return null;
    }

    private void finishSuccess(IntegrationSyncLog syncLog, Instant startedAt, int processed, int failed) {
        Instant finishedAt = Instant.now();
        syncLog.setStatus(SyncStatus.SUCCESS);
        syncLog.setFinishedAt(finishedAt);
        syncLog.setDurationMs(finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        syncLog.setItemsProcessed(processed);
        syncLog.setItemsFailed(failed);
        syncLogRepository.save(syncLog);
    }
}
