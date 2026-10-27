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
    void rankAware_shouldClassifyAsRootWhenParentDoesNotResolveInProject() {
        WorkItem task = workItem("task-1", "Task", "missing-parent");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(task, levels, resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
    }

    @Test
    void rankAware_shouldTreatSameRankParentAsChild() {
        // Same rank is allowed (Bug under Story at the same level) -- child, not orphan.
        WorkItem taskA = workItem("task-a", "Task", null);
        WorkItem taskB = workItem("task-b", "Task", "task-a");
        List<HierarchyLevel> levels = List.of(new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 1));

        var result = classifier.classify(taskB, levels, resolverOf(taskA));

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
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
    void unrankedParentType_shouldStillClassifyAsChildNotOrphan() {
        // Regression: a real org declared hierarchyLevels as Epic/Feature/Bug/Task/Issue but never
        // ranked "User Story". Every Task parented to a User Story then resolved to a parent with
        // no rank and was routed to ORPHAN, stranding 7 of 19 items in the "Sem item pai" bucket
        // even though their parent links were perfectly valid. An unranked parent type means the
        // ordering is unknown, not violated — fall back to structural inference for that link.
        WorkItem story = workItem("story-1", "User Story", "feature-1");
        WorkItem task = workItem("task-1", "Task", "story-1");
        List<HierarchyLevel> levels = List.of(
                new HierarchyLevel("Epic", 0), new HierarchyLevel("Feature", 1),
                new HierarchyLevel("Bug", 2), new HierarchyLevel("Task", 2),
                new HierarchyLevel("Issue", 2));

        var result = classifier.classify(task, levels, resolverOf(story));

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void unrankedParentType_shouldBecomeRootWhenParentDoesNotResolve() {
        // The unranked-parent fallback must not swallow genuinely broken links: if the parent
        // doesn't resolve in the project at all, it's still an orphan.
        WorkItem task = workItem("task-1", "Task", "story-missing");
        List<HierarchyLevel> levels = List.of(
                new HierarchyLevel("Epic", 0), new HierarchyLevel("Task", 2));

        var result = classifier.classify(task, levels, resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
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
    void structuralFallback_shouldClassifyUnresolvableParentAsRoot() {
        WorkItem item = workItem("wi-1", "Task", "missing");

        var result = classifier.classify(item, List.of(), resolverOf());

        assertThat(result).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
    }

    // ---- backlog-arvore-nivel (D1) ----

    private static final List<HierarchyLevel> LEVELS = List.of(
            new HierarchyLevel("Project", 0), new HierarchyLevel("Epic", 1),
            new HierarchyLevel("Feature", 2), new HierarchyLevel("Story", 3),
            new HierarchyLevel("Bug", 3), new HierarchyLevel("Task", 4));

    @Test
    void place_missingParentIsRootFlaggedParentMissing() {
        WorkItem epic = workItem("10", "Epic", "7153");

        var placement = classifier.place(epic, LEVELS, resolverOf());

        assertThat(placement.classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(placement.parentMissing()).isTrue();
    }

    @Test
    void place_noParentIsRootNotFlagged() {
        var placement = classifier.place(workItem("10", "Epic", null), LEVELS, resolverOf());

        assertThat(placement.classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(placement.parentMissing()).isFalse();
    }

    @Test
    void place_withoutHierarchyLevelsMissingParentIsFlaggedRoot() {
        var placement = classifier.place(workItem("10", "Task", "99"), List.of(), resolverOf());

        assertThat(placement.classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(placement.parentMissing()).isTrue();
    }

    @Test
    void place_bugUnderStoryAtSameRankIsChild() {
        WorkItem story = workItem("1", "Story", null);
        WorkItem bug = workItem("2", "Bug", "1");

        assertThat(classifier.place(bug, LEVELS, resolverOf(story)).classification())
                .isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void place_featureUnderStoryIsOrphan() {
        WorkItem story = workItem("1", "Story", null);
        WorkItem feature = workItem("2", "Feature", "1");

        var placement = classifier.place(feature, LEVELS, resolverOf(story));

        assertThat(placement.classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ORPHAN);
        assertThat(placement.parentMissing()).isFalse();
    }

    @Test
    void place_selfReferenceIsRootNotFlagged() {
        var placement = classifier.place(workItem("5", "Story", "5"), LEVELS, resolverOf());

        assertThat(placement.classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(placement.parentMissing()).isFalse();
    }

    @Test
    void cycleAtSameRank_lowestIdIsRootOtherStaysChild() {
        WorkItem s10 = workItem("10", "Story", "20");
        WorkItem s20 = workItem("20", "Story", "10");

        var all = classifier.classifyAll(List.of(s10, s20), LEVELS);

        assertThat(all.get("10").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(all.get("10").parentMissing()).isFalse();
        assertThat(all.get("20").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
        // the single-item form agrees with the batch
        assertThat(classifier.classify(s10, LEVELS, resolverOf(s10, s20)))
                .isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(classifier.classify(s20, LEVELS, resolverOf(s10, s20)))
                .isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void cycleCompareIdsNumerically() {
        WorkItem s9 = workItem("9", "Story", "100");
        WorkItem s100 = workItem("100", "Story", "9");

        var all = classifier.classifyAll(List.of(s9, s100), LEVELS);

        assertThat(all.get("9").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
        assertThat(all.get("100").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
    }

    @Test
    void tailLeadingIntoCycleStaysChild() {
        WorkItem a = workItem("1", "Story", "2");
        WorkItem b = workItem("2", "Story", "1");
        WorkItem tail = workItem("3", "Task", "2");

        var all = classifier.classifyAll(List.of(a, b, tail), LEVELS);

        assertThat(all.get("3").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
        assertThat(all.get("1").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ROOT);
    }

    @Test
    void classifyAll_agreesWithSingleItemClassification() {
        WorkItem epic = workItem("1", "Epic", "999");
        WorkItem story = workItem("2", "Story", "1");
        WorkItem feat = workItem("3", "Feature", "2");
        List<WorkItem> items = List.of(epic, story, feat);

        var all = classifier.classifyAll(items, LEVELS);

        for (WorkItem it : items) {
            assertThat(all.get(it.getId()).classification())
                    .isEqualTo(classifier.classify(it, LEVELS, resolverOf(epic, story, feat)));
        }
        assertThat(all.get("1").parentMissing()).isTrue();
        assertThat(all.get("2").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.CHILD);
        assertThat(all.get("3").classification()).isEqualTo(WorkItemHierarchyClassifier.Classification.ORPHAN);
    }

    @Test
    void rankOf_isCaseInsensitiveAndNullWhenUnranked() {
        assertThat(classifier.rankOf(workItem("1", " epic", null), LEVELS)).isEqualTo(1);
        assertThat(classifier.rankOf(workItem("1", "Issue", null), LEVELS)).isNull();
    }

    private WorkItem workItem(String externalId, String rawType, String parentExternalId) {
        return new WorkItem(externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title " + externalId, rawType, null,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, List.of(), null, null, null, null, parentExternalId,
                NOW, null, null, null, null, true, null, NOW,
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
