package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AllocationTypeEligibilityTest {

    private static final String ORG = "org-1";

    @Mock private AzureProjectBindingRepository bindingRepository;
    @InjectMocks private AllocationTypeEligibility eligibility;

    /** Default rule stand-in: keeps only the TASK-typed items, so it is distinguishable from the marked-types rule. */
    private static final Function<List<WorkItem>, List<WorkItem>> TASKS_ONLY =
            items -> items.stream().filter(i -> "Task".equals(i.getRawType())).toList();

    private void binding(String projectId, TypeMapping... mappings) {
        WorkflowConfig config = mock(WorkflowConfig.class);
        lenient().when(config.getTypeMappings()).thenReturn(List.of(mappings));
        AzureProjectBinding binding = mock(AzureProjectBinding.class);
        lenient().when(binding.getWorkflowConfig()).thenReturn(config);
        lenient().when(bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, projectId))
                .thenReturn(Optional.of(binding));
    }

    private static TypeMapping mapping(String raw, boolean ai) {
        return new TypeMapping(raw, WorkItem.ResolvedType.STORY, ai);
    }

    private static WorkItem item(String id, String project, String rawType, boolean active) {
        WorkItem wi = mock(WorkItem.class);
        lenient().when(wi.getId()).thenReturn(id);
        lenient().when(wi.getProjectId()).thenReturn(project);
        lenient().when(wi.getRawType()).thenReturn(rawType);
        lenient().when(wi.isActive()).thenReturn(active);
        return wi;
    }

    @Test
    void should_applyDefaultRule_when_noTypeIsMarked() {
        binding("p1", mapping("User Story", false), mapping("Task", false));
        WorkItem story = item("a", "p1", "User Story", true);
        WorkItem task = item("b", "p1", "Task", true);

        assertThat(eligibility.eligibleUnits(ORG, List.of(story, task), TASKS_ONLY)).containsExactly(task);
        assertThat(eligibility.isEligible(ORG, story)).isTrue();
    }

    @Test
    void should_returnOnlyMarkedTypes_ignoringCase_andIgnoringLevel() {
        binding("p1", mapping("user story", true), mapping("BUG", true), mapping("Task", false));
        WorkItem us = item("a", "p1", "User Story", true);
        WorkItem bug = item("b", "p1", "Bug", true);
        WorkItem feature = item("c", "p1", "Feature", true);
        WorkItem task = item("d", "p1", "Task", true);

        assertThat(eligibility.eligibleUnits(ORG, List.of(us, bug, feature, task), TASKS_ONLY))
                .containsExactly(us, bug);
        assertThat(eligibility.isEligible(ORG, feature)).isFalse();
        assertThat(eligibility.isEligible(ORG, bug)).isTrue();
    }

    @Test
    void should_applyDefaultRule_when_projectHasNoBinding() {
        WorkItem task = item("a", "p9", "Task", true);
        WorkItem story = item("b", "p9", "User Story", true);

        assertThat(eligibility.eligibleUnits(ORG, List.of(story, task), TASKS_ONLY)).containsExactly(task);
        assertThat(eligibility.isEligible(ORG, story)).isTrue();
    }

    @Test
    void should_decideProjectByProject() {
        binding("A", mapping("Task", true), mapping("User Story", false));
        binding("B", mapping("Task", false), mapping("User Story", false));
        WorkItem aStory = item("1", "A", "User Story", true);
        WorkItem aTask = item("2", "A", "Task", true);
        WorkItem bStory = item("3", "B", "User Story", true);
        WorkItem bTask = item("4", "B", "Task", true);

        // A: marked rule (Task). B: default rule (TASKS_ONLY here too, so use identity to tell them apart)
        List<WorkItem> result = eligibility.eligibleUnits(ORG, List.of(aStory, aTask, bStory, bTask), items -> items);

        assertThat(result).containsExactly(aTask, bStory, bTask);
    }

    @Test
    void should_dropInactiveItems_when_typesAreMarked_andKeepInputOrder() {
        binding("p1", mapping("Bug", true), mapping("User Story", true));
        WorkItem inactive = item("a", "p1", "Bug", false);
        WorkItem us = item("b", "p1", "User Story", true);
        WorkItem bug = item("c", "p1", "Bug", true);

        assertThat(eligibility.eligibleUnits(ORG, List.of(bug, inactive, us), items -> items))
                .containsExactly(bug, us);
    }

    @Test
    void should_loadEachBindingOncePerCall() {
        binding("p1", mapping("Bug", true));
        eligibility.eligibleUnits(ORG, List.of(item("a", "p1", "Bug", true), item("b", "p1", "Bug", true)), i -> i);

        verify(bindingRepository, times(1)).findByOrganizationIdAndProjectIdAndActiveTrue(ORG, "p1");
    }

    @Test
    void should_returnEmpty_forEmptyInput() {
        assertThat(eligibility.eligibleUnits(ORG, List.of(), i -> i)).isEmpty();
    }
}
