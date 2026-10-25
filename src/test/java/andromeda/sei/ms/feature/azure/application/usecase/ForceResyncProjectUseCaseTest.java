package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateWorkItemMetricsUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForceResyncProjectUseCaseTest {

    @Mock private AzureConnectionRepository connectionRepository;
    @Mock private AzureProjectBindingRepository bindingRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;
    @Mock private CalculateWorkItemMetricsUseCase calculateWorkItemMetricsUseCase;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;

    private ForceResyncProjectUseCase useCase;

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String ORG_URL = "https://dev.azure.com/myorg";
    private static final String AUTH_HEADER = "Basic dGVzdA==";

    @BeforeEach
    void setUp() {
        useCase = new ForceResyncProjectUseCase(
                connectionRepository, bindingRepository, workItemRepository,
                syncAzureWorkItemsUseCase, calculateWorkItemMetricsUseCase, authStrategyResolver);
    }

    @Test
    void should_rebuildTransitions_for_all_items_and_return_summary() {
        AzureConnection connection = connection();
        AzureProjectBinding binding = binding(null);

        when(connectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(Optional.of(binding));
        when(authStrategyResolver.resolve(connection)).thenReturn(AUTH_HEADER);

        // 3 work items â€” each gets rebuildTransitionHistory called
        WorkItem item1 = workItem("wi-1", 4.0, Instant.now().minusSeconds(3600), Instant.now());
        WorkItem item2 = workItem("wi-2", 8.0, Instant.now().minusSeconds(7200), Instant.now());
        WorkItem item3 = workItem("wi-3", null, null, null); // no metrics

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(item1, item2, item3));

        // After recalculation: items 1 and 2 have cycleTime, item 3 doesn't
        WorkItem refreshed1 = workItem("wi-1", 4.0, Instant.now().minusSeconds(3600), Instant.now());
        refreshed1.setCycleTimeHours(4.0);
        refreshed1.setLeadTimeHours(8.0);
        WorkItem refreshed2 = workItem("wi-2", 8.0, Instant.now().minusSeconds(7200), Instant.now());
        refreshed2.setCycleTimeHours(8.0);
        refreshed2.setLeadTimeHours(16.0);
        WorkItem refreshed3 = workItem("wi-3", null, null, null);

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(item1, item2, item3))
                .thenReturn(List.of(refreshed1, refreshed2, refreshed3));

        ForceResyncProjectUseCase.ResyncSummaryResponse summary = useCase.execute(ORG, PROJECT);

        // rebuildTransitionHistory called once per item
        verify(syncAzureWorkItemsUseCase, times(3))
                .rebuildTransitionHistory(any(WorkItem.class), any(), eq(ORG_URL), eq(AUTH_HEADER));

        // calculateWorkItemMetrics called once for the project
        verify(calculateWorkItemMetricsUseCase).execute(ORG, PROJECT);

        assertThat(summary.itemsProcessed()).isEqualTo(3);
        assertThat(summary.itemsWithCycleTime()).isEqualTo(2);
        assertThat(summary.itemsWithLeadTime()).isEqualTo(2);
    }

    @Test
    void should_throw_when_connection_not_found() {
        when(connectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, PROJECT))
                .isInstanceOf(AzureConnectionNotFoundException.class);
    }

    @Test
    void should_throw_when_binding_not_found() {
        when(connectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection()));
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, PROJECT))
                .isInstanceOf(AzureBindingNotFoundException.class);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private AzureConnection connection() {
        return new AzureConnection(
                "conn-1", ORG, ORG_URL, "token", null, null,
                "user-1", AzureConnectionStatus.CONNECTED, null, 0,
                null, null, true, Instant.now(), Instant.now(), null,
                AzureAuthMethod.PAT, "my-pat");
    }

    private AzureProjectBinding binding(WorkflowConfig config) {
        return new AzureProjectBinding(
                "binding-1", ORG, PROJECT, "azure-proj-1", "My Project",
                true, null, config, true, Instant.now(), Instant.now(), null);
    }

    private WorkItem workItem(String id, Double storyPoints, Instant startedAt, Instant completedAt) {
        WorkItem wi = new WorkItem(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), "Title " + id,
                "Story", WorkItem.ResolvedType.STORY,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", storyPoints, List.of(),
                "sprint-1", "ext-user", "user-1", null, null,
                Instant.now(), startedAt, completedAt,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);
        return wi;
    }

    @Test
    void should_skipInactiveItems_when_rebuilding() {
        AzureConnection connection = connection();
        when(connectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));
        when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(Optional.of(binding(null)));
        when(authStrategyResolver.resolve(connection)).thenReturn(AUTH_HEADER);

        WorkItem active = workItem("wi-active", 4.0, Instant.now().minusSeconds(3600), Instant.now());
        WorkItem removed = workItem("wi-removed", 4.0, Instant.now().minusSeconds(3600), Instant.now());
        removed.setActive(false);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(active, removed));

        ForceResyncProjectUseCase.ResyncSummaryResponse summary = useCase.execute(ORG, PROJECT);

        verify(syncAzureWorkItemsUseCase, times(1))
                .rebuildTransitionHistory(eq(active), any(), eq(ORG_URL), eq(AUTH_HEADER));
        verify(syncAzureWorkItemsUseCase, never())
                .rebuildTransitionHistory(eq(removed), any(), any(), any());
        assertThat(summary.itemsProcessed()).isEqualTo(1);
    }
}
