package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateWorkItemMetricsUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForceResyncProjectUseCase {

    private final AzureConnectionRepository connectionRepository;
    private final AzureProjectBindingRepository bindingRepository;
    private final WorkItemRepository workItemRepository;
    private final SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    private final CalculateWorkItemMetricsUseCase calculateWorkItemMetricsUseCase;
    private final AzureAuthStrategyResolver authStrategyResolver;

    public ResyncSummaryResponse execute(String organizationId, String projectId) {
        AzureConnection connection = connectionRepository
                .findByOrganizationIdAndActiveTrue(organizationId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(organizationId));

        AzureProjectBinding binding = bindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId)
                .orElseThrow(() -> new AzureBindingNotFoundException(projectId));

        String orgUrl = connection.getAzureOrgUrl();
        String authHeader = authStrategyResolver.resolve(connection);
        WorkflowConfig config = binding.getWorkflowConfig();

        List<WorkItem> items = workItemRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId).stream()
                .filter(WorkItem::isActive)
                .toList();
        log.info("ForceResync: rebuilding transition history for {} work items in org={} project={}",
                items.size(), organizationId, projectId);

        int itemsProcessed = 0;

        for (WorkItem item : items) {
            try {
                syncAzureWorkItemsUseCase.rebuildTransitionHistory(item, config, orgUrl, authHeader);
                itemsProcessed++;
            } catch (Exception e) {
                log.warn("ForceResync: failed to rebuild transitions for workItemId={}: {}",
                        item.getId(), e.getMessage());
            }
        }

        // Recalculate cycleTimeHours and leadTimeHours for all items in the project
        calculateWorkItemMetricsUseCase.execute(organizationId, projectId);

        // Re-query to count items with populated metrics
        List<WorkItem> refreshed = workItemRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId).stream()
                .filter(WorkItem::isActive)
                .toList();
        int itemsWithCycleTime = (int) refreshed.stream()
                .filter(wi -> wi.getCycleTimeHours() != null)
                .count();
        int itemsWithLeadTime = (int) refreshed.stream()
                .filter(wi -> wi.getLeadTimeHours() != null)
                .count();

        log.info("ForceResync complete: org={} project={} processed={} cycleTime={} leadTime={}",
                organizationId, projectId, itemsProcessed, itemsWithCycleTime, itemsWithLeadTime);

        return new ResyncSummaryResponse(itemsProcessed, itemsWithCycleTime, itemsWithLeadTime);
    }

    public record ResyncSummaryResponse(int itemsProcessed, int itemsWithCycleTime, int itemsWithLeadTime) {}
}
