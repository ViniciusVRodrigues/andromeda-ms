package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectBacklogClassificationServiceTest {

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final Instant NOW = Instant.now();

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AzureProjectBindingRepository azureProjectBindingRepository;

    @Mock
    private WorkItemRepository workItemRepository;

    @InjectMocks
    private ProjectBacklogClassificationService service;

    private final WorkItemHierarchyClassifier classifier = new WorkItemHierarchyClassifier();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG_ID);
        service = new ProjectBacklogClassificationService(
                projectRepository, azureProjectBindingRepository, workItemRepository, classifier,
                new WorkItemHierarchyMetricsCalculator(classifier));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReportTotalElementsMatchingReturnedContent_forRoots() {
        givenActiveProject();
        givenNoHierarchyBinding();

        // 3 roots (no parent), 2 orphans (unresolved parent) — 5 active items total.
        List<WorkItem> items = List.of(
                item("root-1", null),
                item("root-2", null),
                item("root-3", null),
                item("orphan-1", "missing-parent-a"),
                item("orphan-2", "missing-parent-b"));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(WorkItem::getExternalId)
                .containsExactly("root-1", "root-2", "root-3");
    }

    @Test
    void shouldReportTotalElementsMatchingReturnedContent_forOrphans() {
        givenActiveProject();
        givenNoHierarchyBinding();

        List<WorkItem> items = List.of(
                item("root-1", null),
                item("orphan-1", "missing-parent-a"),
                item("orphan-2", "missing-parent-b"));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ORPHAN, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void shouldPaginateFilteredResultsWithoutShortPages() {
        givenActiveProject();
        givenNoHierarchyBinding();

        // 5 roots total; page size 2 should yield full pages of 2, 2, then 1 — never an empty
        // page while totalElements still claims more.
        List<WorkItem> items = List.of(
                item("root-1", null), item("root-2", null), item("root-3", null),
                item("root-4", null), item("root-5", null));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        WorkItemPageResult page0 = service.execute(PROJECT_ID, Classification.ROOT, 0, 2);
        WorkItemPageResult page1 = service.execute(PROJECT_ID, Classification.ROOT, 1, 2);
        WorkItemPageResult page2 = service.execute(PROJECT_ID, Classification.ROOT, 2, 2);

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page2.getTotalElements()).isEqualTo(5);
    }

    @Test
    void shouldReturnEmptyPageWhenRequestedPageIsBeyondFilteredResults() {
        givenActiveProject();
        givenNoHierarchyBinding();

        List<WorkItem> items = List.of(item("root-1", null));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 5, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldExcludeInactiveItemsFromClassification() {
        givenActiveProject();
        givenNoHierarchyBinding();

        WorkItem inactiveRoot = inactiveItem("inactive-1", null);
        WorkItem activeRoot = item("root-1", null);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(inactiveRoot, activeRoot));

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(WorkItem::getExternalId).containsExactly("root-1");
    }

    @Test
    void shouldThrowWhenProjectNotFoundForOrg() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(PROJECT_ID, Classification.ROOT, 0, 20))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void shouldApplyRankAwareClassificationUsingBindingHierarchyLevels() {
        givenActiveProject();
        AzureProjectBinding binding = binding(new WorkflowConfig(
                List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.CUSTOM,
                List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1))));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG_ID, PROJECT_ID))
                .thenReturn(Optional.of(binding));

        WorkItem epic = itemWithType("epic-1", null, "Epic");
        WorkItem childTask = itemWithType("task-1", "epic-1", "Task");
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(epic, childTask));

        WorkItemPageResult roots = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);

        assertThat(roots.getTotalElements()).isEqualTo(1);
        assertThat(roots.getContent()).extracting(WorkItem::getExternalId).containsExactly("epic-1");
    }

    private void givenActiveProject() {
        Project project = new Project(PROJECT_ID, ORG_ID, "Project", "project", null,
                true, List.of(), 0L, NOW, NOW);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private void givenNoHierarchyBinding() {
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG_ID, PROJECT_ID))
                .thenReturn(Optional.empty());
    }

    private AzureProjectBinding binding(WorkflowConfig config) {
        return new AzureProjectBinding("binding-1", ORG_ID, PROJECT_ID, "azure-proj-1", "Azure Project",
                true, null, config, true, NOW, NOW, 0L);
    }

    private WorkItem item(String externalId, String parentExternalId) {
        return itemWithType(externalId, parentExternalId, "Task");
    }

    private WorkItem itemWithType(String externalId, String parentExternalId, String rawType) {
        return new WorkItem(externalId, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title", rawType, WorkItem.ResolvedType.TASK,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, List.of(), null, null, null, null,
                parentExternalId, NOW, null, null,
                null, null,
                true, 0L, NOW,
                null, null, null, null,
                null, null, null, null);
    }

    private WorkItem inactiveItem(String externalId, String parentExternalId) {
        return new WorkItem(externalId, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title", "Task", WorkItem.ResolvedType.TASK,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, List.of(), null, null, null, null,
                parentExternalId, NOW, null, null,
                null, null,
                false, 0L, NOW,
                null, null, null, null,
                null, null, null, null);
    }
}
