package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.EffortBearingLevel;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the three ways teams model complexity/effort, which previously only worked for model 1
 * because dashboards and AI allocation hard-coded "STORY or FEATURE" and ignored TASK.
 */
class EffortBearingLevelResolverTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final EffortBearingLevelResolver resolver = new EffortBearingLevelResolver();

    // ---- Model 1: effort AND complexity on the Story ----

    @Test
    void model1_shouldTreatStoriesAsUnitsAndUseTheirOwnEffort() {
        WorkItem storyA = story("1", null, 5.0, 3.0);
        WorkItem storyB = story("2", null, 8.0, 5.0);
        List<WorkItem> items = List.of(storyA, storyB);

        var level = resolver.resolve(EffortBearingLevel.AUTO, items);
        assertThat(level).isEqualTo(EffortBearingLevel.STORY);

        assertThat(resolver.deliverableUnits(level, items)).containsExactly(storyA, storyB);
        assertThat(resolver.totalEffort(items, items)).isEqualTo(13.0);
    }

    @Test
    void model1_storyOwnEffortWinsOverChildTasks() {
        // The decisive rule: the Story's own number is what the team agreed on, and its Tasks
        // restate the same work at finer granularity — summing both would double count it.
        WorkItem story = story("1", null, 8.0, 5.0);
        WorkItem taskA = task("2", "1", 3.0);
        WorkItem taskB = task("3", "1", 4.0);
        List<WorkItem> items = List.of(story, taskA, taskB);

        assertThat(resolver.effectiveEffort(story, items)).isEqualTo(8.0);
        // 8, not 8+7 and not 7
        assertThat(resolver.totalEffort(List.of(story), items)).isEqualTo(8.0);
    }

    // ---- Model 2: complexity on the Story, effort on its Tasks ----

    @Test
    void model2_shouldRollTaskEffortIntoTheParentStory() {
        WorkItem story = story("1", null, null, 5.0);
        WorkItem taskA = task("2", "1", 3.0);
        WorkItem taskB = task("3", "1", 4.0);
        List<WorkItem> items = List.of(story, taskA, taskB);

        var level = resolver.resolve(EffortBearingLevel.AUTO, items);
        assertThat(level).isEqualTo(EffortBearingLevel.TASK);

        // The story has no effort of its own, so it inherits the sum of its tasks.
        assertThat(resolver.effectiveEffort(story, items)).isEqualTo(7.0);
        // Complexity still comes from the story itself.
        assertThat(resolver.effectiveComplexity(story, items)).isEqualTo(5.0);
    }

    @Test
    void model2_totalCountsEachDeliveryOnce() {
        WorkItem storyA = story("1", null, null, 5.0);
        WorkItem taskA = task("2", "1", 3.0);
        WorkItem storyB = story("3", null, null, 2.0);
        WorkItem taskB = task("4", "3", 4.0);
        List<WorkItem> items = List.of(storyA, taskA, storyB, taskB);

        // Whether counted at story level (3+4 via rollup) or task level (3+4 directly), the
        // sprint total is 7 -- never 14.
        assertThat(resolver.totalEffort(List.of(storyA, storyB), items)).isEqualTo(7.0);
        assertThat(resolver.totalEffort(List.of(taskA, taskB), items)).isEqualTo(7.0);
    }

    // ---- Model 3: effort AND complexity on the Task ----

    @Test
    void model3_shouldTreatTasksAsDeliverableUnits() {
        WorkItem taskA = task("1", null, 3.0);
        WorkItem taskB = task("2", null, 4.0);
        List<WorkItem> items = List.of(taskA, taskB);

        var level = resolver.resolve(EffortBearingLevel.AUTO, items);
        assertThat(level).isEqualTo(EffortBearingLevel.TASK);

        // Previously these were filtered out entirely, so velocity read zero.
        assertThat(resolver.deliverableUnits(level, items)).containsExactly(taskA, taskB);
        assertThat(resolver.totalEffort(items, items)).isEqualTo(7.0);
    }

    // ---- Configuration ----

    @Test
    void shouldHonourAnExplicitlyPinnedLevelOverInference() {
        WorkItem story = story("1", null, 5.0, 3.0);
        WorkItem task = task("2", "1", 3.0);
        List<WorkItem> items = List.of(story, task);

        assertThat(resolver.resolve(EffortBearingLevel.STORY, items)).isEqualTo(EffortBearingLevel.STORY);
        assertThat(resolver.resolve(EffortBearingLevel.TASK, items)).isEqualTo(EffortBearingLevel.TASK);
    }

    @Test
    void shouldFallBackToStoryTierWhenPinnedToTaskButNoTasksExist() {
        // An org mid-migration shouldn't get an empty dashboard.
        WorkItem story = story("1", null, 5.0, 3.0);
        List<WorkItem> items = List.of(story);

        assertThat(resolver.deliverableUnits(EffortBearingLevel.TASK, items)).containsExactly(story);
    }

    @Test
    void should_summarizeVelocityAndPlanned_when_tasksCarryTheEffort() {
        WorkItem story = andromeda.sei.ms.feature.workitem.domain.WorkItemFixture.item("s",
                WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE, null, null);
        WorkItem done1 = andromeda.sei.ms.feature.workitem.domain.WorkItemFixture.item("t1",
                WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 3.0, story.getExternalId());
        WorkItem done2 = andromeda.sei.ms.feature.workitem.domain.WorkItemFixture.item("t2",
                WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 5.0, story.getExternalId());

        var summary = resolver.summarize(List.of(story, done1, done2));

        assertThat(summary.delivered()).isEqualTo(8.0);
        assertThat(summary.planned()).isEqualTo(8.0);
        assertThat(summary.doneUnits()).hasSize(2);
    }

    @Test
    void should_reuseOneIndex_when_scopeComputesManyUnits() {
        WorkItem story = andromeda.sei.ms.feature.workitem.domain.WorkItemFixture.item("s",
                WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE, null, null);
        WorkItem task = andromeda.sei.ms.feature.workitem.domain.WorkItemFixture.item("t",
                WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 2.0, story.getExternalId());
        var scope = resolver.scope(List.of(story, task));

        assertThat(scope.effectiveEffort(story)).isEqualTo(2.0);
        assertThat(scope.totalEffort(List.of(story, story))).isEqualTo(4.0);
    }

    @Test
    void shouldReportNullTotalWhenNothingCarriesEffort() {
        WorkItem story = story("1", null, null, null);

        assertThat(resolver.totalEffort(List.of(story), List.of(story))).isNull();
    }

    // ---- complexity-effort: complexity units, task effort ----

    private WorkItem done(WorkItem wi) {
        wi.setResolvedStatus(WorkItem.ResolvedStatus.DONE);
        return wi;
    }

    @Test
    void should_sumComplexityOfStoryTierUnits_regardlessOfEffortLevel() {
        // Effort sits on Tasks only, so the inferred level is TASK; complexity must still be the Story's.
        WorkItem story = done(story("1", null, null, 3.0));
        WorkItem t1 = done(task("2", "1", 4.0));
        WorkItem t2 = done(task("3", "1", 8.0));

        var summary = resolver.summarize(List.of(story, t1, t2));

        assertThat(summary.deliveredComplexity()).isEqualTo(3.0);
        assertThat(summary.plannedComplexity()).isEqualTo(3.0);
        // effort measures stay on the old units (tasks) for the legacy fields
        assertThat(summary.delivered()).isEqualTo(12.0);
    }

    @Test
    void should_notSumEffortInPlaceOfComplexity() {
        WorkItem story = done(story("1", null, 99.0, 3.0));

        var summary = resolver.summarize(List.of(story));

        assertThat(summary.deliveredComplexity()).isEqualTo(3.0);
        assertThat(summary.plannedComplexity()).isEqualTo(3.0);
    }

    @Test
    void should_useComplexityBySum_when_storyHasNoOwnValue() {
        WorkItem feature = done(workItem("1", null, "Feature", WorkItem.ResolvedType.FEATURE, null, null));
        WorkItem child = story("2", "1", null, 2.0);
        WorkItem child2 = story("3", "1", null, 3.0);

        var scope = resolver.scope(List.of(feature, child, child2));

        assertThat(scope.effectiveComplexity(feature)).isEqualTo(5.0);
        assertThat(scope.totalComplexity(List.of(feature))).isEqualTo(5.0);
    }

    @Test
    void should_countBugWithoutComplexityInCoverage_andAddZero() {
        WorkItem story = done(story("1", null, null, 3.0));
        WorkItem bug = done(workItem("2", null, "Bug", WorkItem.ResolvedType.BUG, null, null));

        var summary = resolver.summarize(List.of(story, bug));

        assertThat(summary.deliveredComplexity()).isEqualTo(3.0);
        assertThat(summary.unitsWithoutComplexity()).isEqualTo(1);
    }

    @Test
    void should_reportNullComplexity_when_noUnitHasComplexity() {
        WorkItem story = done(story("1", null, 5.0, null));

        var summary = resolver.summarize(List.of(story));

        assertThat(summary.deliveredComplexity()).isNull();
        assertThat(summary.plannedComplexity()).isNull();
        assertThat(summary.unitsWithoutComplexity()).isEqualTo(1);
    }

    @Test
    void should_logEffortOfTasksOfDeliveredStories() {
        WorkItem story = done(story("1", null, null, 3.0));
        WorkItem t1 = done(task("2", "1", 4.0));
        WorkItem t2 = done(task("3", "1", 8.0));
        WorkItem bug = done(workItem("4", null, "Bug", WorkItem.ResolvedType.BUG, null, null));

        var summary = resolver.summarize(List.of(story, t1, t2, bug));

        assertThat(summary.effortLogged()).isEqualTo(12.0);
    }

    @Test
    void should_logNullEffort_when_nothingCarriesEffort() {
        WorkItem story = done(story("1", null, null, 3.0));

        assertThat(resolver.summarize(List.of(story)).effortLogged()).isNull();
    }

    @Test
    void taskEffort_shouldIgnoreTheStoryOwnEffort() {
        WorkItem story = story("1", null, 100.0, 3.0);
        WorkItem t1 = task("2", "1", 4.0);
        WorkItem t2 = task("3", "1", 8.0);

        assertThat(resolver.scope(List.of(story, t1, t2)).taskEffort(story)).isEqualTo(12.0);
    }

    @Test
    void taskEffort_shouldUseOwnEffort_when_noTaskChild() {
        WorkItem story = story("1", null, 5.0, 3.0);
        WorkItem task = task("2", null, 6.0);

        var scope = resolver.scope(List.of(story, task));

        assertThat(scope.taskEffort(story)).isEqualTo(5.0);
        assertThat(scope.taskEffort(task)).isEqualTo(6.0);
    }

    @Test
    void taskEffort_shouldBeNull_when_nothingAtAll() {
        WorkItem story = story("1", null, null, 3.0);
        WorkItem emptyTask = task("2", "1", null);

        assertThat(resolver.scope(List.of(story, emptyTask)).taskEffort(story)).isNull();
    }

    @Test
    void taskEffort_shouldReachTasksUnderNestedChildren() {
        WorkItem feature = workItem("1", null, "Feature", WorkItem.ResolvedType.FEATURE, null, null);
        WorkItem story = story("2", "1", null, 3.0);
        WorkItem task = task("3", "2", 7.0);

        assertThat(resolver.scope(List.of(feature, story, task)).taskEffort(feature)).isEqualTo(7.0);
    }

    @Test
    void taskEffort_shouldSurviveCycles() {
        WorkItem a = story("1", "2", 50.0, null);
        WorkItem b = task("2", "1", 4.0);

        assertThat(resolver.scope(List.of(a, b)).taskEffort(a)).isEqualTo(4.0);
    }

    private WorkItem story(String externalId, String parentExternalId, Double effort, Double complexity) {
        return workItem(externalId, parentExternalId, "User Story", WorkItem.ResolvedType.STORY, effort, complexity);
    }

    private WorkItem task(String externalId, String parentExternalId, Double effort) {
        return workItem(externalId, parentExternalId, "Task", WorkItem.ResolvedType.TASK, effort, null);
    }

    private WorkItem workItem(String externalId, String parentExternalId, String rawType,
                               WorkItem.ResolvedType resolvedType, Double effort, Double complexity) {
        return new WorkItem("id-" + externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title " + externalId, rawType, resolvedType,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, parentExternalId,
                NOW, null, null, null, null, null, true, null, NOW,
                complexity, null, null, null, effort, null, null, null);
    }
}
