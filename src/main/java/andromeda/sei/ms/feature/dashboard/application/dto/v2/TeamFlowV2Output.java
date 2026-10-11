package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Team flow v2 of a project and sprint ({@code buildTeamFlow}, dashboards-customizaveis D1), without
 * the blocks that need new collection (board columns, sprint scope changes, remaining hours,
 * handoffs, requested reviews, bugs by severity).
 *
 * <p>State categories are the three recorded today ({@code WorkItem.ResolvedStatus}): {@code TODO}
 * → {@code Proposed}, {@code IN_PROGRESS} → {@code InProgress}, {@code DONE} → {@code Completed}.
 * There is no {@code Resolved} category, so {@code states} is empty and {@code resolvedHours} is
 * always {@code null}.
 *
 * <p>No sprint in the project yet: {@code sprint} and the objects are {@code null}, the lists empty
 * ({@link #withoutSprint}).
 */
public record TeamFlowV2Output(
        SprintMeta sprint,
        String processTemplate,
        List<StateInfo> states,
        List<FlowDay> cfd,
        StateTimes stateTimes,
        Rework rework,
        Scope scope,
        ReviewQueue reviewQueue,
        Instant asOf) {

    public static TeamFlowV2Output withoutSprint(Instant asOf) {
        return new TeamFlowV2Output(null, null, List.of(), List.of(), null, null, null, null, asOf);
    }

    /** A board state and its category; no source records it yet, so the list is always empty. */
    public record StateInfo(String name, String category) {}

    /** Items of the sprint in each category at the end of a working day. */
    public record FlowDay(String date, String label,
                          @JsonProperty("Proposed") int proposed,
                          @JsonProperty("InProgress") int inProgress,
                          @JsonProperty("Completed") int completed) {}

    public record StateTimes(StageTimes current, List<StageTimesPoint> history) {}

    /**
     * Over the units completed in the sprint: {@code queueHours} = from entering the sprint
     * ({@code max(createdAt, sprint start)}) to starting; {@code inProgressHours} = time in
     * {@code IN_PROGRESS} up to the first completion; {@code resolvedHours} always {@code null}.
     */
    public record StageTimes(DurationStat queueHours, DurationStat inProgressHours, DurationStat resolvedHours) {}

    public record StageTimesPoint(String sprintId, String label, String name, DurationStat queueHours,
                                  DurationStat inProgressHours, DurationStat resolvedHours) {}

    /**
     * {@code doneInWindow}: project items (no epics/features) first completed inside the sprint;
     * {@code reopened}: those of them that later left {@code DONE}; {@code ratePct} {@code null}
     * without any completed item.
     */
    public record Rework(List<ItemView> reopened, int doneInWindow, Double ratePct) {}

    /** {@code atRiskSp}: effort of the units not done yet, only for the current sprint. */
    public record Scope(Double atRiskSp) {}

    /**
     * {@code open}, {@code drafts} and {@code byBaseBranch} are the current state of the project's
     * open pull requests (null/empty for a past sprint); {@code labels} count the pull requests
     * created in the sprint.
     */
    public record ReviewQueue(Integer open, Integer drafts, List<BranchCount> byBaseBranch, List<LabelCount> labels) {}

    public record BranchCount(String branch, int count) {}

    public record LabelCount(String label, int count) {}
}
