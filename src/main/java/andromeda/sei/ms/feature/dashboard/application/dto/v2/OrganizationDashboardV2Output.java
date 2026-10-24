package andromeda.sei.ms.feature.dashboard.application.dto.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Organization overview v2 of a period ({@code buildOrganization} of the v2 preview), over the
 * projects where the caller holds {@code DASHBOARD_STRATEGIC} only.
 *
 * <p><b>People:</b> the union of the members of those projects — or every active user of the
 * organization when the caller sees all of its active projects. The AI blocks ({@code kpis.aiCostUsd},
 * {@code aiTokens}, {@code activeAiUsers}, {@code ai.*}) and the people coverage are the usage and
 * links of those people. {@code ai.costByProject} has the cost allocated (validated) to each visible
 * project plus a row ({@code projectId null}) with the people's cost no allocation claimed yet.
 *
 * <p><b>AI cost:</b> {@code kpis.aiCostUsd} (and {@code previous}) is, for every caller, the cost
 * allocated (validated) to the visible projects — one meaning, so {@code aiCostPerSp} is comparable.
 * <b>Partial caller</b> (one who does not see every active project): the unallocated row's
 * {@code costUsd} is {@code null}, so no subtraction isolates what the people spent on hidden
 * projects, and {@code integrations.aiConnector.activeTokens} counts only the people's tokens. The
 * usage figures ({@code ai.costByBucket}, {@code byModel}, {@code tokens},
 * {@code subscription.consumptionUsd}, {@code kpis.aiTokens}) stay the people's whole usage for every
 * caller, as the project overview's consumption does.
 *
 * <p>Series have one row per bucket of {@code period.granularity}, even without data: counts 0,
 * ratios and durations {@code null}.
 */
public record OrganizationDashboardV2Output(
        Period period,
        OrgStats kpis,
        OrgStats previous,
        List<PortfolioRow> portfolio,
        Delivery delivery,
        Engineering engineering,
        Ai ai,
        Integrations integrations,
        Instant asOf) {

    public record Period(int days, String start, String end, String previousStart, String previousEnd,
                         String granularity) {}

    /**
     * Delivery of the visible projects in one period. {@code itemsDone}: STORY, BUG and TASK items
     * completed; {@code spDelivered}, {@code leadTimeHours}, {@code cycleTimeHours}: deliverable units
     * (the single velocity definition); {@code cfrPct}: the delivery-metrics proxy over the union of
     * the projects' merges; {@code prsMerged}/{@code commits}/{@code timeToFirstReviewHours}: each
     * pull request and commit once even when its repository is bound to two projects.
     * {@code projects} and {@code people} only in {@code kpis} ({@code null} in {@code previous}).
     */
    public record OrgStats(Integer projects, Integer people, int itemsDone, Double spDelivered,
                           DurationStat leadTimeHours, DurationStat cycleTimeHours, Double cfrPct, int prsMerged,
                           int commits, DurationStat timeToFirstReviewHours, BigDecimal aiCostUsd, long aiTokens,
                           BigDecimal aiCostPerSp, int activeAiUsers) {}

    /**
     * One visible project. {@code currentSprint}/{@code currentProgressGapPct} {@code null} without a
     * current sprint; {@code lastPredictabilityPct}: the latest past sprint; the other figures are of
     * the period. {@code stalePrs} is the current state. {@code aiCostUsd} is allocated to the project.
     */
    public record PortfolioRow(String projectId, String name, String team, int memberCount,
                               CurrentSprint currentSprint, Double currentProgressGapPct,
                               Double lastPredictabilityPct, Double predictabilityPct, int throughput,
                               Double spDelivered, DurationStat leadTimeHours, Double cfrPct,
                               DurationStat prCycleTimeHours, int stalePrs, BigDecimal aiCostUsd,
                               BigDecimal aiCostPerSp, List<Integer> throughputTrend, Health health) {}

    /** {@code progressPct} = done ÷ planned × 100 ({@code null} without planned effort); {@code elapsedPct} in working days. */
    public record CurrentSprint(String id, String name, Double spDone, Double spPlanned, Double progressPct,
                                Double elapsedPct) {}

    /** {@code level} {@code ok}, {@code attention} (1 reason) or {@code risk} (2+); reasons in pt-BR. */
    public record Health(String level, List<String> reasons) {}

    public record Delivery(List<ThroughputByProjectRow> throughputByProject, List<FlowRow> flow,
                           List<TypeCount> typeMix) {}

    /** Items STORY/BUG/TASK completed per visible project id in the bucket. */
    public record ThroughputByProjectRow(String start, String label, Map<String, Integer> byProject) {}

    public record FlowRow(String start, String label, DurationStat leadTimeHours, DurationStat cycleTimeHours) {}

    public record TypeCount(String type, int count) {}

    public record Engineering(List<ActivityRow> activity, List<RepoRow> repos) {}

    public record ActivityRow(String start, String label, int commits, int opened, int merged,
                              DurationStat timeToFirstReviewHours) {}

    /** {@code project}: name(s) of the visible project(s) bound to it; {@code cfrPct} of the period; open/stale now. */
    public record RepoRow(String repositoryId, String repo, String provider, String project, Double cfrPct,
                          int openPrs, int stalePrs, DurationStat prCycleTimeHours) {}

    public record Ai(List<String> models, List<CostBucket> costByBucket, List<AiModel> byModel,
                     List<ProjectCost> costByProject, List<EditorUse> byEditor, Tokens tokens, Adoption adoption,
                     Subscription subscription) {}

    /** The people's AI cost per model in the bucket. */
    public record CostBucket(String start, String label, Map<String, BigDecimal> byModel) {}

    /** {@code projectId null}: "Não alocado a work items" ({@code costUsd null} for a partial caller). */
    public record ProjectCost(String projectId, String name, BigDecimal costUsd) {}

    public record EditorUse(String editor, long interactions) {}

    public record Tokens(long input, long output) {}

    /** {@code activeUsers} with usage in the period, of {@code people}; {@code connected}: with an active connector token. */
    public record Adoption(int activeUsers, int people, int connected) {}

    /**
     * {@code consumptionUsd}: the people's whole usage in the period; {@code subscriptionUsd} =
     * {@code perUserMonthUsd × people × days/30}; both {@code null} without a configured price.
     * {@code usersAboveSubscription}: people whose monthly usage exceeds it ({@code null} without a price).
     */
    public record Subscription(BigDecimal consumptionUsd, BigDecimal subscriptionUsd, BigDecimal perUserMonthUsd,
                               Integer usersAboveSubscription) {}

    /**
     * Connection states of the organization (no person data, no secret), the sync runs of the visible
     * projects (and the organization-level ones) over the last 14 days, the coverage of the people and
     * of the period's data, and the sources bound to each visible project.
     */
    public record Integrations(ConnectionCard azure, ConnectionCard github, AiConnectorCard aiConnector,
                               List<SyncOutcomeDay> syncByDay, Coverage coverage, List<ProjectSources> projects) {}

    /**
     * {@code status} {@code DISCONNECTED} when there is no connection. The last error carries only its
     * code here — the message stays in {@code /integrations}, which requires the configuration codes.
     */
    public record ConnectionCard(String status, Instant lastSuccessAt, Integer consecutiveFailures,
                                 ConnectionError lastError) {}

    /** {@code at} is {@code null}: the connection keeps the last error, not when it happened. */
    public record ConnectionError(String code, Instant at) {}

    /**
     * Active connector tokens — of the whole organization for a caller who sees every active project,
     * of the people only otherwise; the people's latest upload in the current period.
     */
    public record AiConnectorCard(int activeTokens, Instant lastUploadAt) {}

    public record SyncOutcomeDay(String date, String label, int success, int partial, int failed) {}

    public record Coverage(Double azureLinkedPct, Double githubLinkedPct, Double aiConnectedPct,
                           Double itemsWithEstimatePct, Double prsLinkedPct, Double aiDaysAllocatedPct) {}

    /** {@code azureProject} {@code null} without an Azure binding. */
    public record ProjectSources(String projectId, String name, String azureProject, Boolean azureSyncEnabled,
                                 Instant azureLastSyncAt, List<RepoSource> repos) {}

    public record RepoSource(String repositoryId, String repo, String provider, boolean syncEnabled,
                             Instant lastSyncAt) {}
}
