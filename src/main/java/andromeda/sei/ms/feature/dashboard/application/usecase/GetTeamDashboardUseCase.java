package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Team dashboard of a project (team-dashboard, metrics-correctness).
 *
 * <ul>
 *   <li>Velocity and planned effort follow {@link EffortBearingLevelResolver#summarize} — the same
 *       numbers as the developer and strategic dashboards and {@code sprint_metrics}.</li>
 *   <li>Durations are p50/p90 over completed deliverable units; {@code avg*} stay, deprecated.</li>
 *   <li>AI cost is the cost allocated to the project ({@link ProjectAiCostCalculator}); the window of
 *       an informed sprint ends at the sprint end, not today.</li>
 *   <li>A {@code sprintId} of another organization (or project) answers 404.</li>
 *   <li>An iteration shared by several teams is one sprint per team; the dashboard reads it through
 *       its canonical twin ({@link SharedIterations}) — the one the work items are linked to — and
 *       the velocity trend lists each iteration once.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GetTeamDashboardUseCase {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int VELOCITY_TREND_SPRINTS = 6;
    private static final int INITIALS_LENGTH = 2;
    private static final String UNKNOWN_INITIALS = "?";

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final EffortBearingLevelResolver effortBearingLevelResolver;
    private final AIUsageRepository aiUsageRepository;
    private final UserRepository userRepository;
    private final VcsReadPort vcsReadPort;
    private final ProjectAiCostCalculator projectAiCostCalculator;

    public TeamDashboardOutput execute(String orgId, String authUserId, String projectId,
                                       String sprintId, LocalDate startDate, LocalDate endDate) {
        if (projectId == null) {
            throw new BusinessRuleException("TEAM_DASHBOARD_PROJECT_REQUIRED", "projectId is required");
        }
        Optional<Sprint> sprintOpt = resolveSprint(orgId, projectId, sprintId);
        List<WorkItem> items = sprintOpt
                .map(s -> activeOnly(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(orgId, projectId, s.getId())))
                .orElse(List.of());

        LocalDate from = startDate != null ? startDate
                : sprintOpt.map(s -> toLocalDate(s.getStartDate())).orElse(LocalDate.now().minusDays(DEFAULT_WINDOW_DAYS));
        LocalDate to = endDate != null ? endDate
                : sprintOpt.map(s -> toLocalDate(s.getEndDate())).orElse(LocalDate.now());

        EffortBearingLevelResolver.EffortSummary summary = effortBearingLevelResolver.summarize(items);
        Map<String, List<WorkItem>> unitsByAssignee = summary.units().stream()
                .filter(wi -> wi.getAssigneeUserId() != null)
                .collect(Collectors.groupingBy(WorkItem::getAssigneeUserId));
        Map<String, String> userNameById = userNames(orgId);

        ProjectAiCostCalculator.ProjectAiCost aiCost = projectAiCostCalculator.calculate(
                orgId, projectWorkItemIds(orgId, projectId), unitsByAssignee.keySet(), from, to);

        List<TeamDashboardOutput.MemberMetrics> members = unitsByAssignee.entrySet().stream()
                .map(e -> buildMemberMetrics(e.getKey(), e.getValue(), summary.scope(), aiCost, userNameById))
                .sorted(Comparator.comparingDouble(m -> -m.storyPointsDelivered()))
                .toList();

        List<String> repoIds = vcsReadPort.repositoriesOfProject(orgId, projectId).stream()
                .map(VcsRepository::getId).toList();
        List<WorkItem> done = summary.doneUnits();
        return new TeamDashboardOutput(
                sprintOpt.map(Sprint::getId).orElse(null), sprintOpt.map(Sprint::getName).orElse(null),
                sprintOpt.map(s -> toLocalDateStr(s.getStartDate())).orElse(null),
                sprintOpt.map(s -> toLocalDateStr(s.getEndDate())).orElse(null),
                summary.deliveredOrZero(), average(done, WorkItem::getCycleTimeHours),
                average(done, WorkItem::getLeadTimeHours), aiCost.totalAllocated(),
                buildVelocityTrend(orgId, projectId), members, computeStalePrs(orgId, repoIds, userNameById),
                Percentiles.of(done.stream().map(WorkItem::getCycleTimeHours).toList()),
                Percentiles.of(done.stream().map(WorkItem::getLeadTimeHours).toList()),
                reviewMetrics(orgId, repoIds, from, to), aiCost.totalUnallocated());
    }

    private Optional<Sprint> resolveSprint(String orgId, String projectId, String sprintId) {
        if (sprintId == null) {
            return sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(orgId, projectId);
        }
        Sprint sprint = sprintRepository.findByIdAndOrganizationIdAndActiveTrue(sprintId, orgId)
                .filter(s -> projectId.equals(s.getProjectId()))
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
        return Optional.of(canonicalTwin(orgId, sprint));
    }

    /** Another team's twin of a shared iteration holds no work items: read through the canonical one. */
    private Sprint canonicalTwin(String orgId, Sprint sprint) {
        if (sprint.getPath() == null) return sprint;
        return SharedIterations.canonicalTwin(sprint,
                sprintRepository.findAllByOrganizationIdAndProjectIdAndPath(orgId, sprint.getProjectId(), sprint.getPath()));
    }

    private Set<String> projectWorkItemIds(String orgId, String projectId) {
        return workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId).stream()
                .filter(WorkItem::isActive)
                .map(WorkItem::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** Review metrics of the project's pull requests created in the window {@code [from, to]}. */
    private ReviewMetrics reviewMetrics(String orgId, List<String> repoIds, LocalDate from, LocalDate to) {
        if (repoIds.isEmpty()) return ReviewMetrics.empty();
        List<VcsPullRequest> created = vcsReadPort.pullRequestsCreatedIn(orgId, repoIds,
                from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        return ReviewMetricsPolicy.aggregate(created, Instant.now());
    }

    /**
     * Open, non-draft PRs with no first review yet and older than 24h, across the project's bound
     * repos (design.md D1/D3). A project with no bound repo simply yields an empty list.
     */
    private List<TeamDashboardOutput.StalePrSummary> computeStalePrs(String orgId, List<String> repoIds,
                                                                       Map<String, String> userNameById) {
        if (repoIds.isEmpty()) return List.of();
        Instant now = Instant.now();
        return vcsReadPort.openPullRequests(orgId, repoIds)
                .stream()
                .filter(pr -> StalePullRequestPolicy.isStale(pr, now))
                .map(pr -> new TeamDashboardOutput.StalePrSummary(pr.getId(), pr.getTitle(),
                        resolveAuthorName(pr, userNameById), pr.getCreatedAt(),
                        Duration.between(pr.getCreatedAt(), now).toHours(), pr.getAuthorLogin(),
                        pr.getProvider() != null ? pr.getProvider().name() : null))
                .sorted(Comparator.comparingDouble(TeamDashboardOutput.StalePrSummary::hoursOpen).reversed())
                .toList();
    }

    /** Linked account name first, raw provider login when the author has no linked User. */
    private String resolveAuthorName(VcsPullRequest pr, Map<String, String> userNameById) {
        if (pr.getAuthorUserId() != null && userNameById.containsKey(pr.getAuthorUserId())) {
            return userNameById.get(pr.getAuthorUserId());
        }
        return pr.getAuthorLogin();
    }

    private TeamDashboardOutput.MemberMetrics buildMemberMetrics(String userId, List<WorkItem> units,
                                                                   EffortBearingLevelResolver.EffortScope scope,
                                                                   ProjectAiCostCalculator.ProjectAiCost aiCost,
                                                                   Map<String, String> userNameById) {
        List<WorkItem> done = units.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .toList();
        Double delivered = scope.totalEffort(done);
        String name = userNameById.getOrDefault(userId, userId);

        return new TeamDashboardOutput.MemberMetrics(
                userId, name, buildInitials(name), delivered == null ? 0d : delivered,
                average(done, WorkItem::getCycleTimeHours), average(done, WorkItem::getLeadTimeHours),
                aiCost.allocatedOf(userId), average(units, WorkItem::getTokensPerSp), units.size(),
                Percentiles.of(done.stream().map(WorkItem::getCycleTimeHours).toList()),
                Percentiles.of(done.stream().map(WorkItem::getLeadTimeHours).toList()),
                aiCost.unallocatedOf(userId));
    }

    /**
     * Last sprints (past and current) with planned and delivered effort per the single velocity
     * definition. Cost and tokens stay the members' totals in the sprint span.
     */
    private List<TeamDashboardOutput.VelocityPoint> buildVelocityTrend(String orgId, String projectId) {
        List<Sprint> pastAndCurrent = SharedIterations.distinct(
                        sprintRepository.findAllByOrganizationIdAndProjectId(orgId, projectId)).stream()
                .filter(Sprint::isActive)
                .filter(s -> s.getTimeFrame() == Sprint.TimeFrame.PAST || s.getTimeFrame() == Sprint.TimeFrame.CURRENT)
                .sorted(Comparator.comparing(s -> s.getStartDate() != null ? s.getStartDate() : Instant.EPOCH))
                .toList();
        int size = pastAndCurrent.size();
        List<TeamDashboardOutput.VelocityPoint> trend = new ArrayList<>();
        for (Sprint sprint : pastAndCurrent.subList(Math.max(0, size - VELOCITY_TREND_SPRINTS), size)) {
            trend.add(velocityPoint(orgId, projectId, sprint));
        }
        return trend;
    }

    private TeamDashboardOutput.VelocityPoint velocityPoint(String orgId, String projectId, Sprint sprint) {
        List<WorkItem> sprintItems = activeOnly(
                workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(orgId, projectId, sprint.getId()));
        EffortBearingLevelResolver.EffortSummary summary = effortBearingLevelResolver.summarize(sprintItems);
        LocalDate sprintStart = toLocalDate(sprint.getStartDate());
        LocalDate sprintEnd = toLocalDate(sprint.getEndDate());
        BigDecimal sprintAiCost = null;
        Long sprintTokens = null;
        if (sprintStart != null && sprintEnd != null) {
            List<String> userIds = sprintItems.stream().map(WorkItem::getAssigneeUserId)
                    .filter(Objects::nonNull).distinct().toList();
            sprintAiCost = aiUsageRepository.findTotalCostByUserIdsAndDateRange(orgId, userIds, sprintStart, sprintEnd)
                    .values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            sprintTokens = aiUsageRepository.findTotalTokensByUserIdsAndDateRange(orgId, userIds, sprintStart, sprintEnd)
                    .values().stream().filter(Objects::nonNull).reduce(0L, Long::sum);
        }
        return new TeamDashboardOutput.VelocityPoint(sprint.getId(), sprint.getName(),
                summary.plannedOrZero(), summary.deliveredOrZero(), sprintAiCost, sprintTokens);
    }

    /**
     * Display name per user id. Users without name fall back to email, then to the id itself, and
     * duplicated ids keep the first — {@code Collectors.toMap} would otherwise throw on either.
     */
    private Map<String, String> userNames(String orgId) {
        return userRepository.findAllByOrganizationId(orgId).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, this::displayName, (first, second) -> first));
    }

    private String displayName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return user.getId();
    }

    private static List<WorkItem> activeOnly(List<WorkItem> items) {
        return items.stream().filter(WorkItem::isActive).toList();
    }

    /** Deprecated average, kept for one release next to the percentiles. */
    private static Double average(List<WorkItem> items, Function<WorkItem, Double> metric) {
        OptionalDouble avg = items.stream().map(metric).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    private String buildInitials(String name) {
        if (name == null || name.isBlank()) return UNKNOWN_INITIALS;
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(INITIALS_LENGTH, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private static LocalDate toLocalDate(Instant instant) {
        return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static String toLocalDateStr(Instant instant) {
        LocalDate ld = toLocalDate(instant);
        return ld != null ? ld.toString() : null;
    }
}
