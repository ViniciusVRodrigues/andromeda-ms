package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemHierarchyMetricsCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final List<HierarchyLevel> LEVELS = List.of(
            new HierarchyLevel("Epic", 0),
            new HierarchyLevel("Feature", 1),
            new HierarchyLevel("Task", 3));

    private WorkItemHierarchyMetricsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new WorkItemHierarchyMetricsCalculator(new WorkItemHierarchyClassifier());
    }

    @Test
    void shouldFlagHasChildrenOnlyForItemsWithValidChildren() {
        WorkItem epic = workItem("1", "Epic", null, null, null);
        WorkItem feature = workItem("2", "Feature", "1", null, null);
        WorkItem task = workItem("3", "Task", "2", null, 5.0);

        var metrics = calculator.calculate(List.of(epic, feature, task), LEVELS);

        assertThat(metrics.get(epic.getId()).hasChildren()).isTrue();
        assertThat(metrics.get(feature.getId()).hasChildren()).isTrue();
        assertThat(metrics.get(task.getId()).hasChildren()).isFalse();
    }

    @Test
    void shouldSumEffortAndComplexityAcrossAllDescendants() {
        // The real shape that motivated this: complexity lives on the middle level, effort on the
        // leaves, so the Epic has no own value of either but must still report both rollups.
        WorkItem epic = workItem("1", "Epic", null, null, null);
        WorkItem story = workItem("2", "User Story", "1", 4.0, null);
        WorkItem taskA = workItem("3", "Task", "2", null, 8.0);
        WorkItem taskB = workItem("4", "Task", "2", null, 2.0);

        var metrics = calculator.calculate(List.of(epic, story, taskA, taskB), LEVELS);

        WorkItemHierarchyMetrics epicMetrics = metrics.get(epic.getId());
        assertThat(epicMetrics.rolledUpComplexity()).isEqualTo(4.0);
        assertThat(epicMetrics.rolledUpEffort()).isEqualTo(10.0);
        assertThat(epicMetrics.descendantCount()).isEqualTo(3);

        // A story with multiple tasks sums them -- the 1:1 shape must not be assumed.
        WorkItemHierarchyMetrics storyMetrics = metrics.get(story.getId());
        assertThat(storyMetrics.rolledUpEffort()).isEqualTo(10.0);
        assertThat(storyMetrics.descendantCount()).isEqualTo(2);
    }

    @Test
    void shouldReportNullRollupWhenNoDescendantCarriesAValue() {
        // Distinguishes "nothing below has a value" from a genuine zero.
        WorkItem epic = workItem("1", "Epic", null, null, null);
        WorkItem feature = workItem("2", "Feature", "1", null, null);

        var metrics = calculator.calculate(List.of(epic, feature), LEVELS);

        assertThat(metrics.get(epic.getId()).rolledUpComplexity()).isNull();
        assertThat(metrics.get(epic.getId()).rolledUpEffort()).isNull();
        assertThat(metrics.get(epic.getId()).hasChildren()).isTrue();
    }

    @Test
    void shouldExcludeOwnValueFromItsOwnRollup() {
        WorkItem story = workItem("1", "User Story", null, 4.0, 3.0);
        WorkItem task = workItem("2", "Task", "1", null, 8.0);

        var metrics = calculator.calculate(List.of(story, task), LEVELS);

        // 8 from the task only -- the story's own 3.0 is not double counted.
        assertThat(metrics.get(story.getId()).rolledUpEffort()).isEqualTo(8.0);
    }

    @Test
    void shouldNotCountRankViolatingChildAsChild() {
        // An Epic (rank 0) parented under a Task (rank 3) violates the declared order: the tree
        // routes it to orphans, so the Task must not advertise an expand toggle for it.
        WorkItem task = workItem("1", "Task", null, null, 5.0);
        WorkItem epic = workItem("2", "Epic", "1", null, 9.0);

        var metrics = calculator.calculate(List.of(task, epic), LEVELS);

        assertThat(metrics.get(task.getId()).hasChildren()).isFalse();
        assertThat(metrics.get(task.getId()).rolledUpEffort()).isNull();
    }

    @Test
    void shouldTerminateOnParentCycle() {
        // Malformed Azure data: two items parented to each other. Must not recurse forever.
        WorkItem a = workItem("1", "Task", "2", null, 1.0);
        WorkItem b = workItem("2", "Task", "1", null, 2.0);

        Map<String, WorkItemHierarchyMetrics> metrics =
                calculator.calculate(List.of(a, b), List.of());

        assertThat(metrics).hasSize(2);
        assertThat(metrics.get(a.getId()).rolledUpEffort()).isEqualTo(2.0);
    }

    private WorkItem workItem(String externalId, String rawType, String parentExternalId,
                               Double complexity, Double effort) {
        return new WorkItem("id-" + externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title " + externalId, rawType, null,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, List.of(), null, null, null, null, parentExternalId,
                NOW, null, null, null, null, true, null, NOW,
                complexity, null, null, null, effort, null, null, null);
    }
}
