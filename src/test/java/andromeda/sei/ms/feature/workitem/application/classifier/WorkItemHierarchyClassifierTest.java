package andromeda.sei.ms.feature.workitem.application.classifier;

import andromeda.sei.ms.feature.azure.domain.HierarchyLevel;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemHierarchyClassifierTest {

    private final WorkItemHierarchyClassifier classifier = new WorkItemHierarchyClassifier();

    private static final Instant NOW = Instant.now();

    @Test
    void rankAware_shouldClassifyLowestRankTypeAsRoot() {
        WorkItem epic = workItem("epic-1", "Epic", null);
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(epic, levels, resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
    }

    @Test
    void rankAware_shouldClassifyItemWithNoParentAsRootRegardlessOfType() {
        WorkItem task = workItem("task-1", "Task", null);
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(task, levels, resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
    }

    @Test
    void rankAware_shouldClassifyAsChildWhenParentResolvesWithStrictlyLowerRank() {
        WorkItem epic = workItem("epic-1", "Epic", null);
        WorkItem task = workItem("task-1", "Task", "epic-1");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(task, levels, resolverOf(epic));

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void rankAware_shouldClassifyAsOrphanWhenParentDoesNotResolveInProject() {
        WorkItem task = workItem("task-1", "Task", "missing-parent");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(task, levels, resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ORPHAN);
    }

    @Test
    void rankAware_shouldRouteHierarchyViolatingLinkToOrphan() {
        // Two same-rank Tasks: one points to the other as "parent" — must be orphan, not child.
        WorkItem taskA = workItem("task-a", "Task", null);
        WorkItem taskB = workItem("task-b", "Task", "task-a");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(taskB, levels, resolverOf(taskA));

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ORPHAN);
    }

    @Test
    void rankAware_shouldRouteHigherRankParentingLowerRankToOrphan() {
        // A Task (rank 1) declared as parent of an Epic (rank 0) violates "parent must be strictly
        // lower rank" — must be orphan.
        WorkItem task = workItem("task-1", "Task", null);
        WorkItem epic = workItem("epic-1", "Epic", "task-1");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(epic, levels, resolverOf(task));

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ORPHAN);
    }

    @Test
    void unrankedTypeInData_shouldFallBackStructurallyWithoutBreakingOtherClassification() {
        // "CustomType" has no entry in hierarchyLevels — falls back to structural rule for itself.
        WorkItem customWithParent = workItem("custom-1", "CustomType", "epic-1");
        WorkItem epic = workItem("epic-1", "Epic", null);
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var resultWithParent = classifier.classify(customWithParent, levels, resolverOf(epic));
        assertThat(resultWithParent).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);

        WorkItem customNoParent = workItem("custom-2", "CustomType", null);
        var resultNoParent = classifier.classify(customNoParent, levels, resolverOf());
        assertThat(resultNoParent).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);

        // Ranked items are unaffected by the unranked type's presence.
        WorkItem task = workItem("task-1", "Task", "epic-1");
        var taskResult = classifier.classify(task, levels, resolverOf(epic));
        assertThat(taskResult).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void rankAware_shouldMatchRawTypeCaseAndWhitespaceInsensitively() {
        // The wizard-declared rawType ("Epic"/"Task") can drift in casing/whitespace from what's
        // persisted on a synced WorkItem ("epic "/" TASK") — matching must not silently miss.
        WorkItem epic = workItem("epic-1", "epic ", null);
        WorkItem task = workItem("task-1", " TASK", "epic-1");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var epicResult = classifier.classify(epic, levels, resolverOf());
        assertThat(epicResult).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);

        var taskResult = classifier.classify(task, levels, resolverOf(epic));
        assertThat(taskResult).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void structuralFallback_shouldClassifyNoParentAsRootWhenHierarchyLevelsEmpty() {
        WorkItem item = workItem("wi-1", "Task", null);

        var result = classifier.classify(item, List.of(), resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
    }

    @Test
    void structuralFallback_shouldClassifyResolvableParentAsChildRegardlessOfType() {
        WorkItem parent = workItem("parent-1", "Task", null);
        WorkItem child = workItem("child-1", "Task", "parent-1");

        var result = classifier.classify(child, List.of(), resolverOf(parent));

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void structuralFallback_shouldClassifyUnresolvableParentAsOrphan() {
        WorkItem item = workItem("wi-1", "Task", "missing");

        var result = classifier.classify(item, List.of(), resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ORPHAN);
    }

    private WorkItem workItem(String externalId, String rawType, String parentExternalId) {
        return new WorkItem(externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title " + externalId, rawType, null,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, parentExternalId,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }

    private Function<String, WorkItem> resolverOf(WorkItem... items) {
        Map<String, WorkItem> byExternalId = new java.util.HashMap<>();
        for (WorkItem item : items) {
            byExternalId.put(item.getExternalId(), item);
        }
        return byExternalId::get;
    }
}
