package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.Assignee;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;

import java.time.Instant;
import java.util.List;

/**
 * Project quality v2 of a period ({@code buildQuality} of the v2 preview), without severity,
 * closing reason and GitHub issues (not stored).
 *
 * <p>A bug is an active work item of resolved type {@code BUG}; it is open while not
 * {@code DONE}, wherever it is (backlog or any sprint). A reopen is a transition out of
 * {@code DONE} ({@code WorkItemTransition}). Commit types come from the conventional-commit prefix
 * of the message.
 */
public record ProjectQualityV2Output(
        Period period,
        Kpis kpis,
        List<BugFlowRow> bugFlow,
        List<OpenBug> oldestOpen,
        List<ReworkRow> rework,
        List<CommitKindRow> commitKinds,
        List<CommitKindTotal> commitKindTotals,
        Totals totals,
        Instant asOf) {

    public record Period(int days, String start, String end, String granularity) {}

    /**
     * <ul>
     *   <li>{@code resolutionHours}: lead time (creation → done) of the bugs closed in the period.</li>
     *   <li>{@code reopenRatePct}: of the items (epics and features excluded) completed in the
     *       period, the share that was reopened at least once ({@code reopenedItems}).</li>
     *   <li>{@code fixCommitsPct}: commits of the period typed {@code fix}.</li>
     *   <li>{@code mergedPrs}/{@code remediationPrs}/{@code remediationPrsPct}: pull requests merged
     *       into the default branch in the period and those the {@code RemediationClassifier} counts
     *       as a correction (linked to a bug, remediation branch prefix, or a revert) — the change
     *       fail rate proxy.</li>
     * </ul>
     */
    public record Kpis(int openBugs, int bugsCreated, int bugsClosed, DurationStat resolutionHours,
                       Double reopenRatePct, int reopenedItems, Double fixCommitsPct, int mergedPrs,
                       int remediationPrs, Double remediationPrsPct) {}

    public record BugFlowRow(String start, String label, int created, int closed) {}

    /** {@code ageDays} since creation ({@code null} without a creation date); {@code inSprint}: has a sprint. */
    public record OpenBug(String externalId, String title, Integer priority, Double ageDays, String status,
                          String rawStatus, Assignee assignee, boolean inSprint) {}

    public record ReworkRow(String start, String label, int reopened, Double ratePct) {}

    public record CommitKindRow(String start, String label, int feat, int fix, int refactor, int test, int chore,
                                int docs, int other) {}

    public record CommitKindTotal(String kind, int count) {}

    public record Totals(int commits) {}
}
