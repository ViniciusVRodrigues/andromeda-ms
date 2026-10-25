package andromeda.sei.ms.feature.azure.application.service;

import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureSyncPageLimitExceededException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciles Azure work items removed from a binding (azure-itens-excluidos D1 to D6): items that
 * are no longer returned by the binding's WIQL (deleted, moved or out of the sync filter) are
 * soft-deleted, and inactive items that come back to the WIQL are reported for reactivation.
 *
 * <p>The ID listing is the only authority. The recycle bin merely classifies the reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureWorkItemRemovalReconciler {

    private final WorkItemRepository workItemRepository;
    private final AzureDevOpsClient azureDevOpsClient;

    /**
     * Fetches all matching work item IDs by looping over WIQL pages ordered ascending by
     * System.Id, until a page returns fewer than the page size (last page) or the hard
     * page-count safety cap is reached (in which case it throws rather than looping
     * forever or silently truncating results).
     */
    public List<Integer> fetchAllWorkItemIds(String orgUrl, String azureProjectId, String authHeader,
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

    /**
     * The externalIds currently returned by the binding's WIQL (no date restriction). Empty means
     * "do not reconcile": the listing failed, exceeded the page limit or came back empty.
     */
    public Optional<Set<String>> fetchPresentIds(String orgUrl, String azureProjectId, String authHeader,
                                                 WorkflowConfig config) {
        try {
            return presentIdsOf(fetchAllWorkItemIds(orgUrl, azureProjectId, authHeader, null, config), azureProjectId);
        } catch (Exception e) {
            log.warn("Removal reconciliation skipped for azureProject={}: WIQL id listing failed: {}",
                    azureProjectId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Same as {@link #fetchPresentIds} when the caller already holds the unrestricted listing (a full
     * sync has no date filter), saving a second WIQL round. Empty list means "do not reconcile".
     */
    public Optional<Set<String>> presentIdsOf(List<Integer> unrestrictedIds, String azureProjectId) {
        if (unrestrictedIds.isEmpty()) {
            log.warn("Removal reconciliation skipped for azureProject={}: WIQL returned no ids", azureProjectId);
            return Optional.empty();
        }
        return Optional.of(unrestrictedIds.stream().map(String::valueOf)
                .collect(Collectors.toCollection(HashSet::new)));
    }

    /** Inactive Azure items of the organization that are present again in the WIQL. */
    public Set<String> findReactivations(String organizationId, Set<String> presentIds) {
        if (presentIds == null || presentIds.isEmpty()) return Set.of();
        try {
            return workItemRepository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                    organizationId, WorkItem.Source.AZURE_DEVOPS, presentIds);
        } catch (Exception e) {
            log.warn("Could not look up work items to reactivate for org={}: {}", organizationId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Soft-deletes the project's active Azure items that are absent from {@code presentIds}, except
     * those just written by this sync ({@code processedIds}). Never throws: a failure here must not
     * change the status of the sync.
     *
     * @return how many items were deactivated
     */
    public int deactivateMissing(String organizationId, String projectId, String orgUrl, String azureProjectId,
                                 String authHeader, Set<String> presentIds, Set<String> processedIds) {
        try {
            Set<String> missing = new HashSet<>(workItemRepository
                    .findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(
                            organizationId, projectId, WorkItem.Source.AZURE_DEVOPS));
            missing.removeAll(presentIds);
            missing.removeAll(processedIds);
            if (missing.isEmpty()) return 0;

            Set<String> deleted = new HashSet<>();
            Set<String> lookupFailedFor = new HashSet<>();
            List<Integer> numeric = missing.stream().map(AzureWorkItemRemovalReconciler::toInt)
                    .filter(java.util.Objects::nonNull).sorted().toList();
            try {
                azureDevOpsClient.getDeletedWorkItems(orgUrl, azureProjectId, numeric, authHeader)
                        .forEach(id -> deleted.add(String.valueOf(id)));
            } catch (Exception e) {
                log.warn("Recycle bin lookup failed for azureProject={}, reason will be UNKNOWN: {}",
                        azureProjectId, e.getMessage());
                lookupFailedFor.addAll(missing);
            }

            Instant at = Instant.now();
            int total = 0;
            if (!lookupFailedFor.isEmpty()) {
                total += (int) workItemRepository.deactivateByExternalIds(organizationId, projectId,
                        WorkItem.Source.AZURE_DEVOPS, lookupFailedFor, WorkItem.DeactivationReason.UNKNOWN, at);
            } else {
                Set<String> deletedMissing = missing.stream().filter(deleted::contains)
                        .collect(Collectors.toCollection(HashSet::new));
                Set<String> left = missing.stream().filter(id -> !deleted.contains(id))
                        .collect(Collectors.toCollection(HashSet::new));
                if (!deletedMissing.isEmpty()) {
                    total += (int) workItemRepository.deactivateByExternalIds(organizationId, projectId,
                            WorkItem.Source.AZURE_DEVOPS, deletedMissing,
                            WorkItem.DeactivationReason.DELETED_IN_AZURE, at);
                }
                if (!left.isEmpty()) {
                    total += (int) workItemRepository.deactivateByExternalIds(organizationId, projectId,
                            WorkItem.Source.AZURE_DEVOPS, left, WorkItem.DeactivationReason.LEFT_BINDING, at);
                }
            }
            return total;
        } catch (Exception e) {
            log.warn("Removal reconciliation failed for org={} project={}: {}", organizationId, projectId,
                    e.getMessage());
            return 0;
        }
    }

    private static Integer toInt(String externalId) {
        try {
            return Integer.valueOf(externalId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
