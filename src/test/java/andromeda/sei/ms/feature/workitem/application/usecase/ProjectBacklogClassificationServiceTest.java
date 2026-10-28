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

        // 3 roots (no parent) + 2 roots flagged parentMissing (unresolved parent) = 5, sorted by id.
        List<WorkItem> items = List.of(
                item("root-1", null),
                item("root-2", null),
                item("root-3", null),
                item("orphan-1", "missing-parent-a"),
                item("orphan-2", "missing-parent-b"));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getContent()).extracting(WorkItem::getExternalId)
                .containsExactly("orphan-1", "orphan-2", "root-1", "root-2", "root-3");
        assertThat(result.infoFor(result.getContent().get(0).getId()).parentMissing()).isTrue();
        assertThat(result.infoFor(result.getContent().get(2).getId()).parentMissing()).isFalse();
    }

    @Test
    void shouldReportTotalElementsMatchingReturnedContent_forOrphans() {
        givenActiveProject();
        givenBindingWith(LEVELS);
        // Feature (rank 2) under Story (rank 3) = inverted hierarchy; a missing parent is NOT an orphan.
        List<WorkItem> items = List.of(
                itemWithType("1", null, "Story"),
                itemWithType("2", "1", "Feature"),
                itemWithType("3", "1", "Feature"),
                itemWithType("4", "999", "Feature"));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ORPHAN, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(WorkItem::getExternalId).containsExactly("2", "3");
        assertThat(result.infoFor("2").parentRawType()).isEqualTo("Story");
        assertThat(result.infoFor("2").rank()).isEqualTo(2);
    }

    private static final List<HierarchyLevel> LEVELS = List.of(
            new HierarchyLevel("Epic", 1), new HierarchyLevel("Feature", 2),
            new HierarchyLevel("Story", 3), new HierarchyLevel("Bug", 3), new HierarchyLevel("Task", 4));

    private void givenBindingWith(List<HierarchyLevel> levels) {
        AzureProjectBinding binding = binding(new WorkflowConfig(
                List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.CUSTOM, levels));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG_ID, PROJECT_ID))
                .thenReturn(Optional.of(binding));
    }

    @Test
    void rootsAreOrderedByRankThenNumericIdAndLevelsDescribeWholeResult() {
        givenActiveProject();
        givenBindingWith(LEVELS);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(
                itemWithType("82524", null, "Task"),
                itemWithType("60427", null, "Epic"),
                itemWithType("19969", null, "Story"),
                itemWithType("29679", null, "epic"),
                itemWithType("5", null, "Issue"),
                itemWithType("7", null, "Bug")));

        WorkItemPageResult page0 = service.execute(PROJECT_ID, Classification.ROOT, 0, 2);

        assertThat(page0.getContent()).extracting(WorkItem::getExternalId).containsExactly("29679", "60427");
        assertThat(page0.getLevels()).extracting("rank").containsExactly(1, 3, 4, null);
        assertThat(page0.getLevels().get(0).rawTypes()).containsExactly("Epic", "epic");
        assertThat(page0.getLevels().get(0).count()).isEqualTo(2);
        assertThat(page0.getLevels().get(1).rawTypes()).containsExactly("Bug", "Story");
        assertThat(page0.getLevels().get(1).count()).isEqualTo(2);
        assertThat(page0.getLevels().get(3).rawTypes()).containsExactly("Issue");
        assertThat(page0.getLevels().get(3).count()).isEqualTo(1);
        assertThat(page0.infoFor("29679").rank()).isEqualTo(1);

        WorkItemPageResult page1 = service.execute(PROJECT_ID, Classification.ROOT, 1, 2);
        WorkItemPageResult page2 = service.execute(PROJECT_ID, Classification.ROOT, 2, 2);
        assertThat(page1.getContent()).extracting(WorkItem::getExternalId).containsExactly("7", "19969");
        assertThat(page2.getContent()).extracting(WorkItem::getExternalId).containsExactly("82524", "5");
    }

    @Test
    void nonNumericIdsComeAfterNumericOnes() {
        givenActiveProject();
        givenNoHierarchyBinding();
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(
                item("abc", null), item("10", null), item("9", null)));

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);

        assertThat(result.getContent()).extracting(WorkItem::getExternalId).containsExactly("9", "10", "abc");
    }

    @Test
    void pagesAreStableAndComplete_45Roots() {
        givenActiveProject();
        givenNoHierarchyBinding();
        List<WorkItem> items = new java.util.ArrayList<>();
        for (int i = 45; i >= 1; i--) items.add(item(String.valueOf(i * 3), null));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        List<String> seen = new java.util.ArrayList<>();
        for (int p = 0; p < 3; p++) {
            service.execute(PROJECT_ID, Classification.ROOT, p, 20).getContent()
                    .forEach(w -> seen.add(w.getExternalId()));
        }

        assertThat(seen).hasSize(45).doesNotHaveDuplicates();
        assertThat(seen.get(0)).isEqualTo("3");
        assertThat(seen.get(44)).isEqualTo("135");
    }

    @Test
    void filteredListUsesSameOrderAndLevelsDescribeFilteredList() {
        givenActiveProject();
        givenBindingWith(LEVELS);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(
                itemWithType("30", "20", "Task"),
                itemWithType("20", null, "Story"),
                itemWithType("10", null, "Epic"),
                itemWithType("40", null, "Epic")));

        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 0, 20,
                BacklogFilter.of(null, null, "Title"));

        assertThat(result.getContent()).extracting(WorkItem::getExternalId).containsExactly("10", "40", "20", "30");
        assertThat(result.getLevels()).extracting("count").containsExactly(2L, 1L, 1L);
        assertThat(result.infoFor("30").parentRawType()).isEqualTo("Story");
    }

    @Test
    void cycleDoesNotMakeItemsDisappear() {
        givenActiveProject();
        givenBindingWith(LEVELS);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(
                itemWithType("10", "20", "Story"), itemWithType("20", "10", "Story")));

        WorkItemPageResult roots = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);

        assertThat(roots.getContent()).extracting(WorkItem::getExternalId).containsExactly("10");
        assertThat(roots.infoFor("10").parentMissing()).isFalse();
        assertThat(roots.metricsFor("10").hasChildren()).isTrue();
        assertThat(roots.metricsFor("20").hasChildren()).isFalse();
    }

    @Test
    void volume_20kItemsClassifiedAndSortedQuickly() {
        givenActiveProject();
        givenBindingWith(LEVELS);
        List<WorkItem> items = new java.util.ArrayList<>();
        String[] types = {"Epic", "Feature", "Story", "Task"};
        for (int i = 4; i <= 20_000; i++) {
            int depth = i % 4;
            String parent = depth == 0 ? (i % 7 == 0 ? "9999999" : null) : String.valueOf(i - 1);
            items.add(itemWithType(String.valueOf(i), parent, types[depth]));
        }
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(items);

        long t0 = System.nanoTime();
        WorkItemPageResult result = service.execute(PROJECT_ID, Classification.ROOT, 0, 20);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(result.getContent()).hasSize(20);
        assertThat(result.getTotalElements()).isEqualTo(5_000);
        assertThat(ms).isLessThan(5_000);
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
