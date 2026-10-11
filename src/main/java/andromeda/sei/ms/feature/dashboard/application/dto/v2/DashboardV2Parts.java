package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Shapes shared by the v2 dashboard payloads (dashboards-customizaveis D1): the format of the
 * {@code buildIndividual}/{@code buildTeam} aggregators of the v2 preview. Days are ISO dates
 * ({@code yyyy-MM-dd}) with a {@code dd/MM} label; a {@code null} metric means "no data", never 0.
 */
public final class DashboardV2Parts {

    private DashboardV2Parts() {}

    /** {@code dayIndex}/{@code dayCount}: working days (Mon–Fri) elapsed up to today / in the sprint. */
    public record SprintMeta(String id, String name, String teamName, String startDate, String endDate,
                             String timeFrame, int dayIndex, int dayCount) {}

    /** {@code remaining} is {@code null} for days still to come and when no unit carries effort. */
    public record BurndownDay(String date, String label, Double remaining, Double ideal) {}

    public record Assignee(String id, String name) {}

    public record ItemPullRequest(Integer number, String repo, String status, boolean stale) {}

    /**
     * A work item of the sprint. {@code effort} is the item's own field; {@code daysInProgress} only
     * for items in progress with a start date; {@code aiCostUsd} is the validated allocation cost.
     */
    public record ItemView(String id, String externalId, String title, String type, String status,
                           String rawStatus, String priority, Double effort, Assignee assignee,
                           Instant startedAt, Instant completedAt, Double cycleTimeHours, Double leadTimeHours,
                           Double daysInProgress, BigDecimal aiCostUsd, Double tokensPerSp, ItemPullRequest pr) {}

    public record WorkItemRef(String externalId, String title) {}

    /**
     * A pull request. {@code author} is the linked user's name, or {@code @login} when the provider
     * account is not linked ({@code authorLinked = false}). {@code hoursOpen} only while open.
     */
    public record PullRequestView(String id, Integer number, String title, String repo, String provider,
                                  String status, String author, boolean authorLinked, boolean draft,
                                  String baseBranch, List<String> labels, Instant createdAt, Instant mergedAt,
                                  Double hoursOpen, Double timeToFirstReviewHours, Double cycleTimeHours,
                                  boolean stale, WorkItemRef workItem) {}

    public record CommitDay(String date, String label, int commits) {}

    public record AiDay(String date, String label, long inputTokens, long outputTokens, long interactions,
                        BigDecimal costUsd) {}

    public record AiModel(String model, long tokens, BigDecimal costUsd, long interactions) {}

    /** Distinct (person, day) pairs with AI usage, by allocation situation. */
    public record AllocationSummary(int validatedDays, int pendingDays, int unallocatedDays) {}
}
