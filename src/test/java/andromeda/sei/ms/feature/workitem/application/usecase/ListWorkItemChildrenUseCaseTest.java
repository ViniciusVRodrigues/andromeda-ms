package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListWorkItemChildrenUseCaseTest {

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private AzureProjectBindingRepository azureProjectBindingRepository;

    private ListWorkItemChildrenUseCase useCase;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        useCase = new ListWorkItemChildrenUseCase(workItemRepository, azureProjectBindingRepository,
                new WorkItemHierarchyClassifier());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnChildren() {
        WorkItem parent = workItem("parent-1", "Epic", null);
        WorkItem child = workItem("child-1", "Task", "parent-1");
        when(workItemRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue("org-1", "proj-1"))
                .thenReturn(Optional.empty());
        when(workItemRepository.findChildCandidatesByOrganizationIdAndParentExternalId(
                "org-1", "proj-1", "parent-1", ListWorkItemChildrenUseCase.CHILDREN_SOFT_CAP + 1))
                .thenReturn(List.of(child));

        var result = useCase.execute("parent-1");

        assertThat(result.children()).containsExactly(child);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void shouldReturnEmptyListForLeaf() {
        WorkItem parent = workItem("parent-1", "Task", null);
        when(workItemRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue("org-1", "proj-1"))
                .thenReturn(Optional.empty());
        when(workItemRepository.findChildCandidatesByOrganizationIdAndParentExternalId(
                "org-1", "proj-1", "parent-1", ListWorkItemChildrenUseCase.CHILDREN_SOFT_CAP + 1))
                .thenReturn(List.of());

        var result = useCase.execute("parent-1");

        assertThat(result.children()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void shouldTruncateAtSoftCap() {
        WorkItem parent = workItem("parent-1", "Epic", null);
        List<WorkItem> manyChildren = new ArrayList<>();
        for (int i = 0; i < ListWorkItemChildrenUseCase.CHILDREN_SOFT_CAP + 1; i++) {
            manyChildren.add(workItem("child-" + i, "Task", "parent-1"));
        }
        when(workItemRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue("org-1", "proj-1"))
                .thenReturn(Optional.empty());
        when(workItemRepository.findChildCandidatesByOrganizationIdAndParentExternalId(
                "org-1", "proj-1", "parent-1", ListWorkItemChildrenUseCase.CHILDREN_SOFT_CAP + 1))
                .thenReturn(manyChildren);

        var result = useCase.execute("parent-1");

        assertThat(result.children()).hasSize(ListWorkItemChildrenUseCase.CHILDREN_SOFT_CAP);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void shouldExcludeRankInvalidCandidatesWhenHierarchyConfigured() {
        WorkItem parent = workItem("parent-1", "Task", null); // rank 1 (leaf) per config below
        WorkItem invalidChild = workItem("child-1", "Epic", "parent-1"); // rank 0, higher than parent
        AzureProjectBinding binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-1", "Azure Proj", true, null,
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.CUSTOM,
                        List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1))),
                true, NOW, NOW, null);
        when(workItemRepository.findById("parent-1")).thenReturn(Optional.of(parent));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue("org-1", "proj-1"))
                .thenReturn(Optional.of(binding));
        when(workItemRepository.findChildCandidatesByOrganizationIdAndParentExternalId(
                "org-1", "proj-1", "parent-1", ListWorkItemChildrenUseCase.CHILDREN_SOFT_CAP + 1))
                .thenReturn(List.of(invalidChild));

        var result = useCase.execute("parent-1");

        assertThat(result.children()).isEmpty();
    }

    @Test
    void shouldThrowWhenWorkItemNotFound() {
        when(workItemRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    void shouldThrowWhenWorkItemBelongsToDifferentOrganization() {
        WorkItem otherOrgItem = workItem("wi-1", "Task", null);
        otherOrgItem.setOrganizationId("org-2");
        when(workItemRepository.findById("wi-1")).thenReturn(Optional.of(otherOrgItem));

        assertThatThrownBy(() -> useCase.execute("wi-1"))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    private WorkItem workItem(String externalId, String rawType, String parentExternalId) {
        return new WorkItem(externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title " + externalId, rawType, null,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, parentExternalId,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }
}
