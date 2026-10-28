package andromeda.sei.ms.feature.dashboard.application;

import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** dashboards-numeros D1: the remaining points fall when a Task with complexity closes, capped by its Story. */
class BurndownCalculatorTest {

    @Test
    void should_dropRemaining_whenTasksUnderAnOpenStoryClose_cappedByTheStory() {
        EffortBearingLevelResolver resolver = new EffortBearingLevelResolver();
        WorkItem story = WorkItemFixture.item("st", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.IN_PROGRESS);
        story.setComplexity(3.0);
        WorkItem t1 = task("t1", 2.0, "2026-08-04T10:00:00Z");
        WorkItem t2 = task("t2", 2.0, "2026-08-05T10:00:00Z");
        List<WorkItem> items = List.of(story, t1, t2);
        var summary = resolver.summarize(items);

        var points = BurndownCalculator.compute(summary.complexityUnits(), summary.scope(),
                LocalDate.parse("2026-08-03"), LocalDate.parse("2026-08-07"), LocalDate.parse("2026-08-07"));

        assertThat(points).extracting(p -> p.remainingPoints()).containsExactly(3.0, 1.0, 0.0, 0.0, 0.0);
    }

    private static WorkItem task(String id, double points, String completedAt) {
        WorkItem t = WorkItemFixture.item(id, WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, null, "ext-st");
        t.setComplexity(points);
        t.setCompletedAt(Instant.parse(completedAt));
        return t;
    }
}
