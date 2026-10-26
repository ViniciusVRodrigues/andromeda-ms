package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.BurndownCalculator;
import andromeda.sei.ms.feature.dashboard.application.v2.ComplexityEffortFigures;
import andromeda.sei.ms.feature.dashboard.application.dto.AiSprintSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.SprintHistoryPoint;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetDeveloperDashboardUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final OrganizationRepository organizationRepository;
    private final VcsReadPort vcsReadPort;
    private final EffortBearingLevelResolver effortBearingLevelResolver;



    /** Window of the comparison history: current sprint + up to 4 previous ones. */
    private static final int SPRINT_HISTORY_LIMIT = 5;

    public DeveloperDashboardOutput execute(String orgId, String userId, String projectId, String sprintId,
                                            LocalDate startDate, LocalDate endDate, String model) {
        Optional<Sprint> sprintOpt = resolveCurrentSprint(orgId, userId, projectId, sprintId);

        List<WorkItem> currentSprintItems = sprintOpt
                .map(sprint -> projectId != null
                        ? workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
                                orgId, userId, sprint.getId(), projectId)
                        : workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(
                                orgId, userId, sprint.getId()))
                .map(list -> list.stream().filter(WorkItem::isActive).toList())
                .orElse(List.of());

        // Which level carries effort depends on how the team models it (all effort on the Story,
        // split Story-complexity/Task-effort, or everything on Tasks). Resolving it from the data
        // is what keeps velocity and burndown from reading zero for the Task-based models.
        var effortLevel = effortBearingLevelResolver.resolve(null, currentSprintItems);
        List<WorkItem> storyItems = effortBearingLevelResolver.deliverableUnits(effortLevel, currentSprintItems);

        List<WorkItem> doneItems = storyItems.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .toList();

        List<WorkItem> inProgressItems = storyItems.stream()
                .filter(wi -> WorkItem.ResolvedStatus.IN_PROGRESS.equals(wi.getResolvedStatus()))
                .toList();

        Double avgCycleTimeHours = average(doneItems.stream()
                .map(WorkItem::getCycleTimeHours)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray());

        Double avgLeadTimeHours = average(doneItems.stream()
                .map(WorkItem::getLeadTimeHours)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray());

        // effectiveEffort: the unit's own value when set, otherwise the sum of its children. Summing
        // both would double count a Story whose Tasks restate the same work at finer granularity.
        EffortBearingLevelResolver.EffortScope effortScope = effortBearingLevelResolver.scope(currentSprintItems);
        Double storyPointsDelivered = effortScope.totalEffort(doneItems);
        Double totalSpInSprint = effortScope.totalEffort(storyItems);

        LocalDate sprintStart = sprintOpt.map(s -> toLocalDate(s.getStartDate())).orElse(null);
        LocalDate sprintEnd = sprintOpt.map(s -> toLocalDate(s.getEndDate())).orElse(null);

        // Token usage series: scoped to the sprint span (zero-filled per day) when a sprint
        // is resolved; otherwise fall back to the explicit range or the trailing 30 days.
        List<DailyAiUsage> tokensByDay;
        if (sprintStart != null && sprintEnd != null) {
            tokensByDay = buildSprintTokenSeries(orgId, userId, sprintStart, sprintEnd, model);
        } else {
            LocalDate from = startDate != null ? startDate : LocalDate.now().minusDays(30);
            LocalDate to = endDate != null ? endDate : LocalDate.now();
            tokensByDay = aiUsageRepository.findDailyUsageByUserIdAndDateRange(
                    orgId, userId, from, to, model);
        }

        BigDecimal sprintCostUsd = null;

        if (sprintStart != null && sprintEnd != null) {
            sprintCostUsd = tokensByDay.stream()
                    .map(DailyAiUsage::costUsd)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Double avgTokensPerSp = average(storyItems.stream()
                .map(WorkItem::getTokensPerSp)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray());

        // Burndown series
        List<BurndownPoint> burndownSeries = computeBurndown(
                effortBearingLevelResolver.deliverableUnits(andromeda.sei.ms.feature.workitem.domain.EffortBearingLevel.STORY,
                        currentSprintItems), effortScope, sprintStart, sprintEnd);

        // AI sprint summary
        AiSprintSummary aiSprintSummary = computeAiSprintSummary(orgId, userId, sprintStart, sprintEnd);

        Map<String, BigDecimal> aiCostByWorkItemId = computeAiCostByWorkItem(orgId, userId, storyItems, sprintStart, sprintEnd);

        BigDecimal subscriptionCostPerUserUsd = organizationRepository.findById(orgId)
                .map(org -> org.getSubscriptionCostPerUserUsd())
                .orElse(null);

        LocalDate prFrom = sprintStart != null ? sprintStart : startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate prTo = sprintEnd != null ? sprintEnd : endDate != null ? endDate : LocalDate.now();
        List<VcsPullRequest> prs = vcsReadPort.pullRequestsByAuthor(orgId, userId,
                prFrom.atStartOfDay(ZoneOffset.UTC).toInstant(), prTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        prs = scopeToProjectRepositories(orgId, projectId, prs);
        int[] prCounts = { (int) prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN).count(),
                (int) prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.MERGED).count() };
        var context = new andromeda.sei.ms.feature.dashboard.application.dto.IndividualSprintContext(
                sprintOpt.map(Sprint::getId).orElse(null), sprintOpt.map(Sprint::getName).orElse(null), prFrom, prTo,
                average(prs.stream().map(VcsPullRequest::getTimeToFirstReviewHours).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray()),
                average(prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.MERGED).map(VcsPullRequest::getCycleTimeHours).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray()),
                (int) prs.stream().filter(pr -> pr.getTimeToFirstReviewHours() != null).count(),
                prs.stream().sorted(Comparator.comparing(VcsPullRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(20).map(pr -> new andromeda.sei.ms.feature.dashboard.application.dto.IndividualSprintContext.PullRequest(
                                pr.getId(), pr.getNumber(), pr.getTitle(), pr.getStatus().name(), pr.getTimeToFirstReviewHours(),
                                pr.getCycleTimeHours(), pr.getLinkedWorkItemExternalId(), pr.getAuthorLogin(),
                                pr.getProvider() != null ? pr.getProvider().name() : null, pr.getAuthorLogin())).toList(),
                ReviewMetricsPolicy.aggregate(prs, Instant.now()),
                Percentiles.of(prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.MERGED)
                        .map(VcsPullRequest::getCycleTimeHours).toList()));

        List<SprintHistoryPoint> sprintHistory =
                computeSprintHistory(orgId, userId, projectId, sprintOpt.orElse(null));

        return new DeveloperDashboardOutput(
                storyItems,
                avgCycleTimeHours,
                avgLeadTimeHours,
                storyPointsDelivered,
                totalSpInSprint,
                tokensByDay,
                sprintCostUsd,
                avgTokensPerSp,
                prCounts[0],
                prCounts[1],
                inProgressItems,
                storyItems.size(),
                burndownSeries,
                aiSprintSummary,
                aiCostByWorkItemId,
                subscriptionCostPerUserUsd,
                sprintHistory,
                context,
                Percentiles.of(doneItems.stream().map(WorkItem::getCycleTimeHours).toList()),
                Percentiles.of(doneItems.stream().map(WorkItem::getLeadTimeHours).toList()),
                ComplexityEffortFigures.of(effortBearingLevelResolver.summarize(currentSprintItems)),
                effortScope
        );
    }

    /**
     * developer-dashboard: with a project informed, only pull requests of repositories bound to
     * it count — the user's work in another project's repositories must not leak into this one.
     */
    private List<VcsPullRequest> scopeToProjectRepositories(String orgId, String projectId,
                                                            List<VcsPullRequest> prs) {
        if (projectId == null || prs.isEmpty()) return prs;
        Set<String> repoIds = vcsReadPort.repositoriesOfProject(orgId, projectId).stream()
                .map(VcsRepository::getId)
                .collect(Collectors.toSet());
        return prs.stream().filter(pr -> repoIds.contains(pr.getRepositoryId())).toList();
    }

    /**
     * Comparison history: up to {@link #SPRINT_HISTORY_LIMIT} sprints ending with the current
     * one, in the same scope (project when given, otherwise the whole org). Each sprint's
     * delivery numbers use the same effort-bearing-level resolution as the current-sprint
     * metrics so the bars are honestly comparable. Sprints without dates are skipped; a
     * sprint with no synced items is kept with zeroed SP (honest empty bar, not omission).
     */
    private List<SprintHistoryPoint> computeSprintHistory(String orgId, String userId,
                                                           String projectId, Sprint current) {
        if (current == null) {
            return List.of();
        }

        // One point per iteration: a shared iteration's other twins hold no items (phantom zero bars).
        List<Sprint> candidates = SharedIterations.distinct(projectId != null
                ? sprintRepository.findAllByOrganizationIdAndProjectId(orgId, projectId)
                : sprintRepository.findAllByOrganizationId(orgId));

        List<Sprint> ordered = candidates.stream()
                .filter(s -> s.getId() != null && s.getStartDate() != null && s.getEndDate() != null)
                .sorted(Comparator.comparing(Sprint::getStartDate))
                .toList();

        int currentIdx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(current.getId())) {
                currentIdx = i;
                break;
            }
        }
        // Window ends at the current sprint; when it is not in the scope list (e.g. resolved
        // by sprintId across projects) fall back to the trailing window.
        int end = currentIdx >= 0 ? currentIdx : ordered.size() - 1;
        int from = Math.max(0, end - (SPRINT_HISTORY_LIMIT - 1));
        List<Sprint> window = ordered.subList(from, end + 1);

        List<SprintHistoryPoint> history = new ArrayList<>();
        for (Sprint s : window) {
            List<WorkItem> items = projectId != null
                    ? workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
                            orgId, userId, s.getId(), projectId)
                    : workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(
                            orgId, userId, s.getId());
            items = items.stream().filter(WorkItem::isActive).toList();
            EffortBearingLevelResolver.EffortSummary summary = effortBearingLevelResolver.summarize(items);
            List<WorkItem> done = summary.doneUnits();
            Double planned = summary.planned();
            Double delivered = summary.delivered();
            Double avgCycle = average(done.stream()
                    .map(WorkItem::getCycleTimeHours)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .toArray());
            AiSprintSummary aiSummary = computeAiSprintSummary(
                    orgId, userId, toLocalDate(s.getStartDate()), toLocalDate(s.getEndDate()));

            history.add(new SprintHistoryPoint(
                    s.getId(),
                    s.getName(),
                    toLocalDate(s.getStartDate()),
                    toLocalDate(s.getEndDate()),
                    planned == null ? 0d : planned,
                    delivered == null ? 0d : delivered,
                    avgCycle,
                    aiSummary == null ? null : aiSummary.totalTokens(),
                    aiSummary == null ? null : aiSummary.totalCostUsd(),
                    Percentiles.of(done.stream().map(WorkItem::getCycleTimeHours).toList()).p50()));
        }
        return history;
    }

    private Map<String, BigDecimal> computeAiCostByWorkItem(String orgId, String userId,
            List<WorkItem> storyItems, LocalDate sprintStart, LocalDate sprintEnd) {
        if (storyItems.isEmpty() || sprintStart == null || sprintEnd == null) return Map.of();

        // Cost basis is the actual usage records an allocation covers, not the whole day's
        // total. Build a per-record cost map so each allocation only attributes the cost of
        // its own aiUsageIds (handles days with partial coverage or multiple allocations).
        Map<String, BigDecimal> costByUsageId = aiUsageRepository
                .findByOrganizationIdAndUserIdAndDayBetween(orgId, userId, sprintStart, sprintEnd)
                .stream()
                .filter(u -> u.getId() != null && u.getCostUsd() != null)
                .collect(Collectors.toMap(
                        AIUsage::getId,
                        AIUsage::getCostUsd,
                        BigDecimal::add));

        Map<String, BigDecimal> costByWorkItem = new HashMap<>();
        for (WorkItem story : storyItems) {
            List<AiUsageAllocation> allocations = allocationRepository
                    .findValidatedByOrgUserAndWorkItemId(orgId, userId, story.getId());
            BigDecimal totalCost = BigDecimal.ZERO;
            for (AiUsageAllocation alloc : allocations) {
                if (alloc.getSplits() == null) continue;

                BigDecimal allocationBasis = alloc.getAiUsageIds() == null ? BigDecimal.ZERO
                        : alloc.getAiUsageIds().stream()
                                .map(id -> costByUsageId.getOrDefault(id, BigDecimal.ZERO))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                for (AllocationSplit split : alloc.getSplits()) {
                    if (!story.getId().equals(split.getWorkItemId())) continue;
                    if (split.getPercentage() == null) continue;
                    totalCost = totalCost.add(allocationBasis.multiply(split.getPercentage()));
                }
            }
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                costByWorkItem.put(story.getId(), totalCost);
            }
        }
        return costByWorkItem;
    }

    /**
     * Builds a token-usage series covering every day of the sprint span, zero-filling days
     * with no usage so the chart renders a continuous X-axis from sprint start to end.
     */
    private List<DailyAiUsage> buildSprintTokenSeries(String orgId, String userId,
            LocalDate sprintStart, LocalDate sprintEnd, String model) {
        Map<LocalDate, DailyAiUsage> byDay = aiUsageRepository
                .findDailyUsageByUserIdAndDateRange(orgId, userId, sprintStart, sprintEnd, model)
                .stream()
                .filter(d -> d.day() != null)
                .collect(Collectors.toMap(DailyAiUsage::day, d -> d, (a, b) -> a));

        List<DailyAiUsage> series = new ArrayList<>();
        LocalDate cursor = sprintStart;
        while (!cursor.isAfter(sprintEnd)) {
            series.add(byDay.getOrDefault(cursor, new DailyAiUsage(cursor, 0L, 0L, 0L, BigDecimal.ZERO)));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /** The shared burndown rule ({@link BurndownCalculator}), up to today. */
    private List<BurndownPoint> computeBurndown(List<WorkItem> sprintItems,
                                                 EffortBearingLevelResolver.EffortScope effortScope,
                                                 LocalDate sprintStart, LocalDate sprintEnd) {
        return BurndownCalculator.compute(sprintItems, effortScope, sprintStart, sprintEnd, LocalDate.now());
    }

    private AiSprintSummary computeAiSprintSummary(String orgId, String userId,
                                                     LocalDate sprintStart, LocalDate sprintEnd) {
        if (sprintStart == null || sprintEnd == null) {
            return null;
        }

        List<AIUsage> sprintUsages = aiUsageRepository
                .findByOrganizationIdAndUserIdAndDayBetween(orgId, userId, sprintStart, sprintEnd);

        if (sprintUsages.isEmpty()) {
            return null;
        }

        long totalTokens = sprintUsages.stream()
                .mapToLong(u -> u.getInputTokens() + u.getOutputTokens())
                .sum();

        BigDecimal totalCostUsd = sprintUsages.stream()
                .map(AIUsage::getCostUsd)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<LocalDate> unallocatedDays = sprintUsages.stream()
                .filter(u -> u.getAllocationId() == null)
                .map(AIUsage::getDay)
                .collect(Collectors.toSet());

        return new AiSprintSummary(totalTokens, totalCostUsd, unallocatedDays.size());
    }


    private Optional<Sprint> resolveCurrentSprint(String orgId, String userId, String projectId, String sprintId) {
        if (sprintId != null) {
            List<Sprint> all = sprintRepository.findAllByOrganizationId(orgId);
            // Another team's twin of a shared iteration holds no items: read through the canonical one.
            return all.stream()
                    .filter(s -> s.isActive() && sprintId.equals(s.getId()) && (projectId == null || projectId.equals(s.getProjectId())))
                    .findFirst()
                    .map(s -> SharedIterations.canonicalTwin(s, all.stream().filter(Sprint::isActive).toList()));
        }
        List<Sprint> current = SharedIterations.distinct(sprintRepository.findCurrentSprintsByOrganizationId(orgId)).stream()
                .filter(s -> s.isActive() && (projectId == null || projectId.equals(s.getProjectId())))
                .sorted(Comparator.comparing(Sprint::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SharedIterations.CANONICAL_ORDER))
                .toList();
        return current.stream().filter(s -> workItemRepository
                .findByOrganizationIdAndAssigneeUserIdAndSprintId(orgId, userId, s.getId()).stream()
                .anyMatch(WorkItem::isActive)).findFirst();
    }

    private Double average(double[] values) {
        if (values.length == 0) return null;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private LocalDate toLocalDate(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}