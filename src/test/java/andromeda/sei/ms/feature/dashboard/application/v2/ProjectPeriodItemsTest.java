package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPeriodItemsTest {

    @Test
    void should_treatOnlyEpicsAndFeaturesAsContainers_andToleratesAMissingType() {
        assertThat(ProjectPeriodItems.isContainer(item(WorkItem.ResolvedType.EPIC))).isTrue();
        assertThat(ProjectPeriodItems.isContainer(item(WorkItem.ResolvedType.FEATURE))).isTrue();
        assertThat(ProjectPeriodItems.isContainer(item(WorkItem.ResolvedType.STORY))).isFalse();
        assertThat(ProjectPeriodItems.isContainer(item(null))).isFalse();
    }

    /** dashboards-numeros D1/D2 (C40): Tasks done under an open Story count by their own completion date. */
    @Test
    void should_deliverPointsAndHoursOfTasksDoneUnderAnOpenStory() {
        WorkItem story = WorkItemFixture.item("st", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.IN_PROGRESS);
        story.setComplexity(5.0);
        WorkItem t1 = doneTask("t1", 3.0, 1.5, "2026-08-10T10:00:00Z");
        WorkItem t2 = doneTask("t2", 5.0, 1.2, "2026-08-12T10:00:00Z");
        WorkItem outside = doneTask("t3", 7.0, 1.0, "2026-07-01T10:00:00Z");
        ProjectPeriodItems items = ProjectPeriodItems.of(List.of(story, t1, t2, outside), List.of(),
                new EffortBearingLevelResolver());
        DayRange range = new DayRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));

        assertThat(items.complexityDeliveredIn(range)).isEqualTo(2.7);
        assertThat(items.effortLoggedIn(range)).isEqualTo(8.0);
    }

    /** The Story closing later must not re-count what its Tasks delivered in the earlier period. */
    @Test
    void should_deliverIncrementally_acrossPeriods_whenTheStoryClosesLater() {
        WorkItem story = WorkItemFixture.item("st", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE);
        story.setComplexity(5.0);
        story.setCompletedAt(Instant.parse("2026-09-05T10:00:00Z"));
        WorkItem t1 = doneTask("t1", 3.0, 1.5, "2026-08-10T10:00:00Z");
        WorkItem t2 = doneTask("t2", 5.0, 1.2, "2026-08-12T10:00:00Z");
        ProjectPeriodItems items = ProjectPeriodItems.of(List.of(story, t1, t2), List.of(),
                new EffortBearingLevelResolver());
        DayRange a = new DayRange(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"));
        DayRange b = new DayRange(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

        assertThat(items.complexityDeliveredIn(a)).isCloseTo(2.7, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(items.complexityDeliveredIn(b)).isCloseTo(2.3, org.assertj.core.data.Offset.offset(1e-9));
    }

    private static WorkItem doneTask(String id, double hours, double points, String completedAt) {
        WorkItem t = WorkItemFixture.item(id, WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, hours, "ext-st");
        t.setComplexity(points);
        t.setCompletedAt(Instant.parse(completedAt));
        return t;
    }

    private static WorkItem item(WorkItem.ResolvedType type) {
        return new WorkItem("wi", "org", "proj", WorkItem.Source.AZURE_DEVOPS,
                "1", Instant.now(), "Title",
                "Raw", type,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", List.of(),
                "sprint", "ext-user", "user", null, null,
                Instant.now(), null, null,
                null, null, true, 1L, Instant.now(), null, null, null, null, 3.0, null, null, null);
    }
}
