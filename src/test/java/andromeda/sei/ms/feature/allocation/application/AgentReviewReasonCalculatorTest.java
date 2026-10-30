package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewCode;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ReviewReason;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** atribuicao-a-prova-de-esquecimento 1.2: ITEM_CLOSED, OTHER_ASSIGNEE, OUT_OF_SPRINT and the day-level spike. */
class AgentReviewReasonCalculatorTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate DAY = LocalDate.of(2026, 10, 29);

    final WorkItemRepository items = mock(WorkItemRepository.class);
    final SprintRepository sprints = mock(SprintRepository.class);
    final UsageSpikeDetector spike = mock(UsageSpikeDetector.class);
    final AgentReviewReasonCalculator calculator = new AgentReviewReasonCalculator(items, sprints, spike);

    WorkItem item(WorkItem.ResolvedStatus status, Instant completedAt, String assignee, String sprintId) {
        return new WorkItem("wi-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS, "123", Instant.now(), "T", "Task",
                WorkItem.ResolvedType.TASK, "x", status, "Medium", List.of(), sprintId, null, assignee, null, null,
                Instant.now(), null, completedAt, null, null, true, 1L, Instant.now(),
                null, null, null, null, null, null, null, null);
    }

    Sprint sprint(String id, String start, String end) {
        return new Sprint(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS, "e-" + id, "t", "T", "S", "p",
                Instant.parse(start), Instant.parse(end), Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
    }

    void given(WorkItem w, List<Sprint> projectSprints) {
        when(items.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(w));
        when(sprints.findAllByOrganizationIdAndProjectId(ORG, "proj-1")).thenReturn(projectSprints);
        when(spike.ratioOf(any(), any(), any())).thenReturn(OptionalLong.empty());
    }

    static final Sprint CURRENT = new Sprint("sp-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS, "e", "t", "T", "S", "p",
            Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), Sprint.TimeFrame.CURRENT,
            true, 1L, Instant.now(), Instant.now());

    @Test
    void should_haveNoReason_forAnOpenItemOfTheUserInTheCurrentSprint() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, USER, "sp-1"), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).isEmpty();
    }

    @Test
    void should_flagItemClosed_whenCompletedBeforeTheDay() {
        given(item(WorkItem.ResolvedStatus.DONE, Instant.parse("2026-10-28T23:00:00Z"), USER, "sp-1"), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1"))
                .containsExactly(new ReviewReason(ReviewCode.ITEM_CLOSED, "wi-1", "AB#123", null));
    }

    @Test
    void should_notFlagItemClosed_whenCompletedOnTheSameDay() {
        given(item(WorkItem.ResolvedStatus.DONE, Instant.parse("2026-10-29T08:00:00Z"), USER, "sp-1"), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).isEmpty();
    }

    @Test
    void should_flagItemClosed_whenDoneWithoutCompletedAt() {
        given(item(WorkItem.ResolvedStatus.DONE, null, USER, "sp-1"), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).extracting(ReviewReason::code)
                .containsExactly(ReviewCode.ITEM_CLOSED);
    }

    @Test
    void should_flagOtherAssignee_onlyWhenFilledAndDifferent() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, "someone-else", "sp-1"), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).extracting(ReviewReason::code)
                .containsExactly(ReviewCode.OTHER_ASSIGNEE);
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, null, "sp-1"), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).isEmpty();
    }

    @Test
    void should_flagOutOfSprint_whenTheItemIsInAnotherSprint() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, USER, "sp-old"),
                List.of(CURRENT, sprint("sp-old", "2026-09-01T00:00:00Z", "2026-09-14T00:00:00Z")));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).extracting(ReviewReason::code)
                .containsExactly(ReviewCode.OUT_OF_SPRINT);
    }

    @Test
    void should_flagOutOfSprint_whenTheItemHasNoSprintButTheProjectHasSprints() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, USER, null), List.of(CURRENT));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).extracting(ReviewReason::code)
                .containsExactly(ReviewCode.OUT_OF_SPRINT);
    }

    @Test
    void should_notFlagOutOfSprint_whenTheProjectHasNoSprints() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, USER, null), List.of());
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).isEmpty();
    }

    @Test
    void should_flagOutOfSprint_whenTheSprintOfTheItemDoesNotHoldTheDay() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, USER, "sp-1"),
                List.of(sprint("sp-1", "2026-10-01T00:00:00Z", "2026-10-14T00:00:00Z")));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).extracting(ReviewReason::code)
                .containsExactly(ReviewCode.OUT_OF_SPRINT);
    }

    @Test
    void should_addTheSpikeAsADayLevelReasonWithTheRatio() {
        given(item(WorkItem.ResolvedStatus.IN_PROGRESS, null, USER, "sp-1"), List.of(CURRENT));
        when(spike.ratioOf(ORG, USER, DAY)).thenReturn(OptionalLong.of(7));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1"))
                .containsExactly(new ReviewReason(ReviewCode.USAGE_SPIKE, null, null, 7L));
    }

    @Test
    void should_stillReportTheSpike_whenTheItemIsUnknown_andReadItemAndSprintsOnce() {
        when(items.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.empty());
        when(spike.ratioOf(ORG, USER, DAY)).thenReturn(OptionalLong.of(5));
        assertThat(calculator.reasonsFor(ORG, USER, DAY, "wi-1")).extracting(ReviewReason::code)
                .containsExactly(ReviewCode.USAGE_SPIKE);

        given(item(WorkItem.ResolvedStatus.DONE, null, "x", null), List.of(CURRENT));
        calculator.reasonsFor(ORG, USER, DAY, "wi-1");
        verify(sprints, times(1)).findAllByOrganizationIdAndProjectId(ORG, "proj-1");
    }
}
