package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dashboards-customizaveis 1.9 — a reopen is a transition out of DONE: the transition right before
 * it (by {@code occurredAt}) ended in DONE and this one does not.
 */
class WorkItemReopensTest {

    @Test
    void should_countOneReopen_when_itemWentDoneThenBackToWork() {
        List<WorkItemTransition> transitions = List.of(
                t("w1", ResolvedStatus.IN_PROGRESS, 1), t("w1", ResolvedStatus.DONE, 2),
                t("w1", ResolvedStatus.IN_PROGRESS, 3), t("w1", ResolvedStatus.DONE, 4));

        assertThat(WorkItemReopens.count(transitions)).isEqualTo(1);
    }

    @Test
    void should_orderByOccurredAt_whatever_theStorageOrder() {
        List<WorkItemTransition> transitions = new ArrayList<>(List.of(
                t("w1", ResolvedStatus.DONE, 4), t("w1", ResolvedStatus.IN_PROGRESS, 1),
                t("w1", ResolvedStatus.IN_PROGRESS, 3), t("w1", ResolvedStatus.DONE, 2)));

        assertThat(WorkItemReopens.count(transitions)).isEqualTo(1);
    }

    @Test
    void should_countDoneToTodo_asAReopen_andIgnoreDoneToDone() {
        List<WorkItemTransition> transitions = List.of(
                t("w1", ResolvedStatus.DONE, 1), t("w1", ResolvedStatus.DONE, 2), t("w1", ResolvedStatus.TODO, 3));

        assertThat(WorkItemReopens.count(transitions)).isEqualTo(1);
    }

    @Test
    void should_notCountAReopen_withoutAPreviousDone() {
        assertThat(WorkItemReopens.count(List.of(t("w1", ResolvedStatus.TODO, 1), t("w1", ResolvedStatus.IN_PROGRESS, 2))))
                .isZero();
        assertThat(WorkItemReopens.count(List.of())).isZero();
    }

    @Test
    void should_ignoreInactiveTransitions_andGroupByWorkItem() {
        WorkItemTransition inactive = t("w2", ResolvedStatus.IN_PROGRESS, 3);
        inactive.setActive(false);
        List<WorkItemTransition> transitions = List.of(
                t("w1", ResolvedStatus.DONE, 1), t("w1", ResolvedStatus.IN_PROGRESS, 2),
                t("w2", ResolvedStatus.DONE, 1), inactive);

        Map<String, Integer> byItem = WorkItemReopens.byWorkItem(transitions);

        assertThat(byItem).containsEntry("w1", 1).containsEntry("w2", 0);
    }

    private static WorkItemTransition t(String workItemId, ResolvedStatus to, int hour) {
        Instant at = Instant.parse("2026-10-01T00:00:00Z").plusSeconds(hour * 3600L);
        return new WorkItemTransition(workItemId + "-" + hour, "org-1", workItemId, "x", to.name(), to, at, at,
                true, 0L, at, at);
    }
}
