package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** dashboards-numeros D1/D2 on the figures every v2 payload (individual, team, project, org) repeats. */
class ComplexityEffortFiguresTest {

    private final EffortBearingLevelResolver resolver = new EffortBearingLevelResolver();

    private List<WorkItem> c40() {
        WorkItem story = WorkItemFixture.item("st", WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.IN_PROGRESS);
        story.setComplexity(5.0);
        story.setAssigneeUserId("u1");
        WorkItem t1 = WorkItemFixture.item("t1", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 3.0, "ext-st");
        t1.setComplexity(1.5);
        WorkItem t2 = WorkItemFixture.item("t2", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 5.0, "ext-st");
        t2.setComplexity(1.2);
        return List.of(story, t1, t2);
    }

    @Test
    void should_showDeliveredPointsAndHours_when_tasksAreDoneUnderAnOpenStory() {
        var figures = ComplexityEffortFigures.of(resolver.summarize(c40()));

        assertThat(figures.complexityPlanned()).isEqualTo(5.0);
        assertThat(figures.complexityDelivered()).isEqualTo(2.7);
        assertThat(figures.effortLogged()).isEqualTo(8.0);
    }

    @Test
    void should_showTheSameForTheAssigneeOfTheStory() {
        var figures = ComplexityEffortFigures.ofAssignee(resolver.summarize(c40()), "u1");

        assertThat(figures.complexityDelivered()).isEqualTo(2.7);
        assertThat(figures.effortLogged()).isEqualTo(8.0);
    }
}
