package andromeda.sei.ms.feature.azure.application.service;

import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureSyncPageLimitExceededException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureWorkItemRemovalReconcilerTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String AZURE_PROJECT = "azure-proj-1";
    private static final String URL = "https://dev.azure.com/org";
    private static final String AUTH = "Bearer x";
    private static final Instant AT = Instant.parse("2026-10-20T10:00:00Z");

    @Mock private WorkItemRepository workItemRepository;
    @Mock private AzureDevOpsClient azureDevOpsClient;

    private AzureWorkItemRemovalReconciler reconciler;
    private final WorkflowConfig config = andromeda.sei.ms.feature.azure.application.DefaultWorkflowConfigFactory.forScrum();

    @BeforeEach
    void setUp() {
        reconciler = new AzureWorkItemRemovalReconciler(workItemRepository, azureDevOpsClient);
    }

    // ---- listing ----

    @Test
    void fetchPresentIds_shouldReturnIdsAsExternalIds() {
        stubPage(List.of(1, 2, 3));

        assertThat(reconciler.fetchPresentIds(URL, AZURE_PROJECT, AUTH, config)).contains(Set.of("1", "2", "3"));
    }

    @Test
    void fetchPresentIds_shouldPageUntilShortPage() {
        List<Integer> full = new ArrayList<>();
        for (int i = 1; i <= AzureDevOpsClient.WIQL_PAGE_SIZE; i++) full.add(i);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(full);
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(),
                eq(AzureDevOpsClient.WIQL_PAGE_SIZE), any()))
                .thenReturn(List.of(9_000_001));

        assertThat(reconciler.fetchPresentIds(URL, AZURE_PROJECT, AUTH, config)).hasValueSatisfying(
                ids -> assertThat(ids).hasSize(AzureDevOpsClient.WIQL_PAGE_SIZE + 1));
    }

    @Test
    void fetchPresentIds_shouldBeEmptyWhenWiqlFails() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenThrow(WebClientResponseException.create(500, "boom", null, null, null));

        assertThat(reconciler.fetchPresentIds(URL, AZURE_PROJECT, AUTH, config)).isEmpty();
    }

    @Test
    void fetchPresentIds_shouldBeEmptyWhenWiqlIsEmpty() {
        stubPage(List.of());

        assertThat(reconciler.fetchPresentIds(URL, AZURE_PROJECT, AUTH, config)).isEmpty();
    }

    @Test
    void fetchPresentIds_shouldBeEmptyWhenPageLimitExceeded() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<Integer> full = new ArrayList<>();
                    Integer last = inv.getArgument(4);
                    int start = last == null ? 0 : last;
                    for (int i = 1; i <= AzureDevOpsClient.WIQL_PAGE_SIZE; i++) full.add(start + i);
                    return full;
                });

        assertThat(reconciler.fetchPresentIds(URL, AZURE_PROJECT, AUTH, config)).isEmpty();
    }

    @Test
    void fetchAllWorkItemIds_shouldThrowPageLimitForTheSyncItself() {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<Integer> full = new ArrayList<>();
                    Integer last = inv.getArgument(4);
                    int start = last == null ? 0 : last;
                    for (int i = 1; i <= AzureDevOpsClient.WIQL_PAGE_SIZE; i++) full.add(start + i);
                    return full;
                });

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> reconciler.fetchAllWorkItemIds(URL, AZURE_PROJECT, AUTH, null, config))
                .isInstanceOf(AzureSyncPageLimitExceededException.class);
    }

    // ---- reactivation ----

    @Test
    void findReactivations_shouldAskOnlyForInactiveOfTheOrgAndSource() {
        when(workItemRepository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                eq(ORG), eq(WorkItem.Source.AZURE_DEVOPS), anyCollection())).thenReturn(Set.of("2"));

        assertThat(reconciler.findReactivations(ORG, Set.of("1", "2", "3"))).containsExactly("2");
    }

    // ---- removal ----

    @Test
    void reconcile_shouldDeactivateDeletedItemsAsDeletedInAzure() {
        activeInAndromeda("1", "2", "3");
        when(azureDevOpsClient.getDeletedWorkItems(URL, AZURE_PROJECT, List.of(2), AUTH)).thenReturn(Set.of(2));
        when(workItemRepository.deactivateByExternalIds(eq(ORG), eq(PROJECT), eq(WorkItem.Source.AZURE_DEVOPS),
                eq(Set.of("2")), eq(WorkItem.DeactivationReason.DELETED_IN_AZURE), any())).thenReturn(1L);

        int n = reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1", "3"), Set.of());

        assertThat(n).isEqualTo(1);
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(),
                eq(WorkItem.DeactivationReason.LEFT_BINDING), any());
    }

    @Test
    void reconcile_shouldDeactivateItemsNotInRecycleBinAsLeftBinding() {
        activeInAndromeda("1", "2");
        when(azureDevOpsClient.getDeletedWorkItems(URL, AZURE_PROJECT, List.of(2), AUTH)).thenReturn(Set.of());
        when(workItemRepository.deactivateByExternalIds(eq(ORG), eq(PROJECT), eq(WorkItem.Source.AZURE_DEVOPS),
                eq(Set.of("2")), eq(WorkItem.DeactivationReason.LEFT_BINDING), any())).thenReturn(1L);

        assertThat(reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1"), Set.of()))
                .isEqualTo(1);
    }

    @Test
    void reconcile_shouldSplitBetweenDeletedAndLeftBinding() {
        activeInAndromeda("1", "2", "3");
        when(azureDevOpsClient.getDeletedWorkItems(eq(URL), eq(AZURE_PROJECT), anyList(), eq(AUTH)))
                .thenReturn(Set.of(2));
        when(workItemRepository.deactivateByExternalIds(any(), any(), any(), eq(Set.of("2")),
                eq(WorkItem.DeactivationReason.DELETED_IN_AZURE), any())).thenReturn(1L);
        when(workItemRepository.deactivateByExternalIds(any(), any(), any(), eq(Set.of("3")),
                eq(WorkItem.DeactivationReason.LEFT_BINDING), any())).thenReturn(1L);

        assertThat(reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1"), Set.of()))
                .isEqualTo(2);
    }

    @Test
    void reconcile_shouldUseUnknownWhenRecycleBinFails_andStillDeactivate() {
        activeInAndromeda("1", "2");
        when(azureDevOpsClient.getDeletedWorkItems(eq(URL), eq(AZURE_PROJECT), anyList(), eq(AUTH)))
                .thenThrow(WebClientResponseException.create(HttpStatus.FORBIDDEN.value(), "no", null, null, null));
        when(workItemRepository.deactivateByExternalIds(eq(ORG), eq(PROJECT), eq(WorkItem.Source.AZURE_DEVOPS),
                eq(Set.of("2")), eq(WorkItem.DeactivationReason.UNKNOWN), any())).thenReturn(1L);

        assertThat(reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1"), Set.of()))
                .isEqualTo(1);
    }

    @Test
    void reconcile_shouldNotDeactivateItemsProcessedInThisRun() {
        activeInAndromeda("1", "2");

        int n = reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1"), Set.of("2"));

        assertThat(n).isZero();
        verify(azureDevOpsClient, never()).getDeletedWorkItems(any(), any(), any(), any());
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reconcile_shouldDoNothingWhenNothingIsMissing() {
        activeInAndromeda("1", "2");

        assertThat(reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1", "2", "3"),
                Set.of())).isZero();
        verify(workItemRepository, never()).deactivateByExternalIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reconcile_shouldSwallowRepositoryErrors() {
        when(workItemRepository.findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(any(), any(), any()))
                .thenThrow(new IllegalStateException("mongo down"));

        assertThat(reconciler.deactivateMissing(ORG, PROJECT, URL, AZURE_PROJECT, AUTH, Set.of("1"), Set.of()))
                .isZero();
    }

    private void activeInAndromeda(String... ids) {
        when(workItemRepository.findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(
                ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS)).thenReturn(Set.of(ids));
    }

    private void stubPage(List<Integer> page) {
        when(azureDevOpsClient.queryWorkItemIdsPage(anyString(), anyString(), anyString(), isNull(), isNull(), any()))
                .thenReturn(page);
    }
}
