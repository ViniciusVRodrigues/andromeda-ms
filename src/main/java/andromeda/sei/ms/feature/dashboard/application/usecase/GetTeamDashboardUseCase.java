package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetTeamDashboardUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final AIUsageRepository aiUsageRepository;
    private final UserRepository userRepository;

    public TeamDashboardOutput execute(String orgId, String authUserId, String projectId,
                                       String sprintId, LocalDate startDate, LocalDate endDate) {
        if (projectId == null) {
            throw new BusinessRuleException("TEAM_DASHBOARD_PROJECT_REQUIRED", "projectId is required");
        }

        Optional<Sprint> sprintOpt = resolveCurrentSprint(orgId, projectId, sprintId);

        String resolvedSprintId = sprintOpt.map(Sprint::getId).orElse(null);
        List<WorkItem> items = resolvedSprintId != null
                ? workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(orgId, projectId, resolvedSprintId)
                : List.of();

        LocalDate from = startDate != null ? startDate
                : sprintOpt.map(s -> toLocalDate(s.getStartDate())).filter(d -> d != null).orElse(LocalDate.now().minusDays(30));
        LocalDate to = endDate != null ? endDate : LocalDate.now();

        // STORY and FEATURE are both developer-assignable, effort-bearing deliverable units
        // (Feature is a container one tier below Epic in Azure hierarchy, but unlike Epic it is
        // routinely story-pointed and completed like a Story in Feature-heavy custom templates) —
        // grouped together here so team velocity isn't silently blind to Feature-typed work.
        List<WorkItem> storyItems = items.stream()
                .filter(wi -> isStoryTierWork(wi.getResolvedType()))
                .toList();

        List<String> memberUserIds = storyItems.stream()
                .map(WorkItem::getAssigneeUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<String, BigDecimal> aiCostByUser = aiUsageRepository.findTotalCostByUserIdsAndDateRange(orgId, memberUserIds, from, to);

        Map<String, String> userNameById = userRepository.findAllByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getName() != null ? u.getName() : u.getEmail()));

        Map<String, List<WorkItem>> byAssignee = storyItems.stream()
                .filter(wi -> wi.getAssigneeUserId() != null)
                .collect(Collectors.groupingBy(WorkItem::getAssigneeUserId));

        List<TeamDashboardOutput.MemberMetrics> members = byAssignee.entrySet().stream()
                .map(entry -> buildMemberMetrics(entry.getKey(), entry.getValue(), aiCostByUser, userNameById))
                .sorted(Comparator.comparingDouble(m -> -m.storyPointsDelivered()))
                .toList();

        double teamVelocity = members.stream().mapToDouble(TeamDashboardOutput.MemberMetrics::storyPointsDelivered).sum();

        Double avgCycleTime = averageNonNull(storyItems.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .map(WorkItem::getCycleTimeHours).toList());
        Double avgLeadTime = averageNonNull(storyItems.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .map(WorkItem::getLeadTimeHours).toList());

        BigDecimal teamAiCostUsd = aiCostByUser.values().stream()
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TeamDashboardOutput.VelocityPoint> velocityTrend = buildVelocityTrend(orgId, projectId, from, to);

        String sprintName = sprintOpt.map(Sprint::getName).orElse(null);
        String sprintStartDate = sprintOpt.map(s -> toLocalDateStr(s.getStartDate())).orElse(null);
        String sprintEndDate = sprintOpt.map(s -> toLocalDateStr(s.getEndDate())).orElse(null);

        return new TeamDashboardOutput(
                resolvedSprintId, sprintName, sprintStartDate, sprintEndDate,
                teamVelocity, avgCycleTime, avgLeadTime, teamAiCostUsd,
                velocityTrend, members
        );
    }

    private Optional<Sprint> resolveCurrentSprint(String orgId, String projectId, String sprintId) {
        if (sprintId != null) {
            return sprintRepository.findById(sprintId);
        }
        return sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(orgId, projectId);
    }

    private TeamDashboardOutput.MemberMetrics buildMemberMetrics(String userId, List<WorkItem> items,
                                                                   Map<String, BigDecimal> aiCostByUser,
                                                                   Map<String, String> userNameById) {
        List<WorkItem> done = items.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .toList();

        double sp = done.stream().mapToDouble(wi -> wi.getEffort() != null ? wi.getEffort() : 0.0).sum();
        Double cycleTime = averageNonNull(done.stream().map(WorkItem::getCycleTimeHours).toList());
        Double leadTime = averageNonNull(done.stream().map(WorkItem::getLeadTimeHours).toList());
        Double avgTokensPerSp = averageNonNull(items.stream().map(WorkItem::getTokensPerSp).toList());

        String name = userNameById.getOrDefault(userId, userId);
        String initials = buildInitials(name);
        BigDecimal aiCost = aiCostByUser.getOrDefault(userId, BigDecimal.ZERO);

        return new TeamDashboardOutput.MemberMetrics(
                userId, name, initials, sp, cycleTime, leadTime, aiCost, avgTokensPerSp, items.size());
    }

    private List<TeamDashboardOutput.VelocityPoint> buildVelocityTrend(String orgId, String projectId,
                                                                         LocalDate from, LocalDate to) {
        List<Sprint> allSprints = sprintRepository.findAllByOrganizationIdAndProjectId(orgId, projectId);
        List<Sprint> pastAndCurrent = allSprints.stream()
                .filter(s -> s.getTimeFrame() == Sprint.TimeFrame.PAST || s.getTimeFrame() == Sprint.TimeFrame.CURRENT)
                .sorted(Comparator.comparing(s -> s.getStartDate() != null ? s.getStartDate() : java.time.Instant.EPOCH))
                .toList();
        int size = pastAndCurrent.size();
        List<Sprint> last6 = pastAndCurrent.subList(Math.max(0, size - 6), size);

        List<TeamDashboardOutput.VelocityPoint> trend = new ArrayList<>();
        for (Sprint sprint : last6) {
            List<WorkItem> sprintItems = workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(
                    orgId, projectId, sprint.getId());
            double sp = sprintItems.stream()
                    .filter(wi -> isStoryTierWork(wi.getResolvedType()))
                    .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                    .mapToDouble(wi -> wi.getEffort() != null ? wi.getEffort() : 0.0)
                    .sum();
            LocalDate sprintStart = toLocalDate(sprint.getStartDate());
            LocalDate sprintEnd = toLocalDate(sprint.getEndDate());
            BigDecimal sprintAiCost = null;
            if (sprintStart != null && sprintEnd != null) {
                List<String> sprintUserIds = sprintItems.stream()
                        .map(WorkItem::getAssigneeUserId).filter(id -> id != null).distinct().toList();
                Map<String, BigDecimal> sprintCost = aiUsageRepository.findTotalCostByUserIdsAndDateRange(
                        orgId, sprintUserIds, sprintStart, sprintEnd);
                sprintAiCost = sprintCost.values().stream().filter(v -> v != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            trend.add(new TeamDashboardOutput.VelocityPoint(sprint.getId(), sprint.getName(), sp, sprintAiCost));
        }
        return trend;
    }

    /**
     * STORY and FEATURE are both treated as developer-assignable, story-point-bearing
     * deliverable work for this dashboard's velocity/cycle-time metrics — see reasoning
     * at the storyItems filter above.
     */
    private boolean isStoryTierWork(WorkItem.ResolvedType resolvedType) {
        return resolvedType == WorkItem.ResolvedType.STORY || resolvedType == WorkItem.ResolvedType.FEATURE;
    }

    private Double averageNonNull(List<Double> values) {
        List<Double> nonNull = values.stream().filter(v -> v != null).toList();
        if (nonNull.isEmpty()) return null;
        return nonNull.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private String buildInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private LocalDate toLocalDate(java.time.Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private String toLocalDateStr(java.time.Instant instant) {
        LocalDate ld = toLocalDate(instant);
        return ld != null ? ld.toString() : null;
    }
}
