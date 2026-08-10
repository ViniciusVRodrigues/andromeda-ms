package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetStrategicDashboardUseCase {

    private static final long DEFAULT_RANGE_DAYS = 90;

    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver
            effortBearingLevelResolver;
    private final UserRepository userRepository;
    private final GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;

    public StrategicDashboardOutput execute(String orgId, String authUserId,
                                             String projectId, String memberId,
                                             LocalDate startDate, LocalDate endDate) {
        LocalDate from = startDate != null ? startDate : LocalDate.now().minusDays(DEFAULT_RANGE_DAYS);
        LocalDate to = endDate != null ? endDate : LocalDate.now();

        if (projectId != null && memberId != null) {
            return levelThree(orgId, memberId, projectId, from, to);
        } else if (projectId != null) {
            return levelTwo(orgId, projectId, from, to);
        } else {
            return levelOne(orgId, from, to);
        }
    }

    private StrategicDashboardOutput levelOne(String orgId, LocalDate from, LocalDate to) {
        List<Project> projects = projectRepository.findAllActiveByOrganizationId(orgId);
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        List<StrategicDashboardOutput.ProjectSummary> summaries = projects.stream()
                .map(project -> buildProjectSummary(orgId, project, fromInstant, toInstant, from, to))
                .toList();

        return new StrategicDashboardOutput("ORG", summaries, null, null);
    }

    private StrategicDashboardOutput.ProjectSummary buildProjectSummary(String orgId, Project project,
                                                                          Instant fromInstant, Instant toInstant,
                                                                          LocalDate from, LocalDate to) {
        List<WorkItem> items = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId())
                .stream()
                .filter(wi -> wi.getCompletedAt() != null
                        && !wi.getCompletedAt().isBefore(fromInstant)
                        && !wi.getCompletedAt().isAfter(toInstant))
                .toList();

        List<WorkItem> done = items.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .toList();

        List<WorkItem> doneStories = done.stream()
                .filter(wi -> effortBearingLevelResolver.isStoryTier(wi.getResolvedType()))
                .toList();

        double sp = doneStories.stream().mapToDouble(wi -> wi.getStoryPoints() != null ? wi.getStoryPoints() : 0.0).sum();
        Double avgCycle = averageNonNull(doneStories.stream().map(WorkItem::getCycleTimeHours).toList());
        Double avgLead = averageNonNull(doneStories.stream().map(WorkItem::getLeadTimeHours).toList());

        StrategicDashboardOutput.DoraMetrics dora = computeDora(done, from, to);

        return new StrategicDashboardOutput.ProjectSummary(
                project.getId(), project.getName(), items.size(), sp, avgCycle, avgLead, dora);
    }

    private StrategicDashboardOutput levelTwo(String orgId, String projectId, LocalDate from, LocalDate to) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> orgId.equals(p.getOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project not found: " + projectId));

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        List<WorkItem> items = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId)
                .stream()
                .filter(wi -> wi.getCompletedAt() != null
                        && !wi.getCompletedAt().isBefore(fromInstant)
                        && !wi.getCompletedAt().isAfter(toInstant))
                .toList();

        Map<String, String> userNameById = userRepository.findAllByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getName() != null ? u.getName() : u.getEmail()));

        Map<String, Boolean> linkedToAzureById = userRepository.findAllByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getAzureId() != null));

        Map<String, List<WorkItem>> byAssignee = items.stream()
                .filter(wi -> wi.getAssigneeUserId() != null)
                .collect(Collectors.groupingBy(WorkItem::getAssigneeUserId));

        List<StrategicDashboardOutput.MemberSummary> members = byAssignee.entrySet().stream()
                .map(entry -> {
                    String uid = entry.getKey();
                    List<WorkItem> memberItems = entry.getValue();
                    List<WorkItem> doneStories = memberItems.stream()
                            .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                            .filter(wi -> effortBearingLevelResolver.isStoryTier(wi.getResolvedType()))
                            .toList();
                    double sp = doneStories.stream().mapToDouble(wi -> wi.getStoryPoints() != null ? wi.getStoryPoints() : 0.0).sum();
                    Double cycleTime = averageNonNull(doneStories.stream().map(WorkItem::getCycleTimeHours).toList());
                    Double leadTime = averageNonNull(doneStories.stream().map(WorkItem::getLeadTimeHours).toList());
                    return new StrategicDashboardOutput.MemberSummary(
                            uid, userNameById.getOrDefault(uid, uid),
                            linkedToAzureById.getOrDefault(uid, false),
                            sp, cycleTime, leadTime, memberItems.size());
                })
                .toList();

        List<WorkItem> done = items.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus())).toList();
        StrategicDashboardOutput.DoraMetrics dora = computeDora(done, from, to);

        StrategicDashboardOutput.ProjectDetail detail = new StrategicDashboardOutput.ProjectDetail(
                project.getId(), project.getName(), members, dora);

        return new StrategicDashboardOutput("PROJECT", null, detail, null);
    }

    private StrategicDashboardOutput levelThree(String orgId, String memberId, String projectId,
                                                 LocalDate from, LocalDate to) {
        DeveloperDashboardOutput devOutput = getDeveloperDashboardUseCase.execute(
                orgId, memberId, projectId, null, from, to, null);
        return new StrategicDashboardOutput("MEMBER", null, null, devOutput);
    }

    private StrategicDashboardOutput.DoraMetrics computeDora(List<WorkItem> doneItems, LocalDate from, LocalDate to) {
        if (doneItems.isEmpty()) {
            return new StrategicDashboardOutput.DoraMetrics(null, "LOW", null, "LOW", null, "ELITE", null, true);
        }

        double weeks = Math.max(1.0, ChronoUnit.DAYS.between(from, to) / 7.0);

        Double avgLeadTime = averageNonNull(doneItems.stream().map(WorkItem::getLeadTimeHours).toList());
        String leadTimeClass = classifyLeadTime(avgLeadTime);

        double deployFreq = doneItems.size() / weeks;
        String deployFreqClass = classifyDeployFreq(deployFreq);

        long bugCount = doneItems.stream()
                .filter(wi -> wi.getResolvedType() == WorkItem.ResolvedType.BUG)
                .count();
        double changeFailureRate = doneItems.isEmpty() ? 0.0 : (bugCount * 100.0) / doneItems.size();
        String changeFailureClass = classifyChangeFailure(changeFailureRate);

        return new StrategicDashboardOutput.DoraMetrics(
                avgLeadTime, leadTimeClass,
                deployFreq, deployFreqClass,
                changeFailureRate, changeFailureClass,
                null, true);
    }

    private String classifyLeadTime(Double hours) {
        if (hours == null) return "LOW";
        if (hours < 1.0) return "ELITE";
        if (hours < 24.0) return "HIGH";
        if (hours < 168.0) return "MEDIUM";
        return "LOW";
    }

    private String classifyDeployFreq(double perWeek) {
        if (perWeek > 7.0) return "ELITE";
        if (perWeek > 1.0) return "HIGH";
        if (perWeek > 0.25) return "MEDIUM";
        return "LOW";
    }

    private String classifyChangeFailure(double percent) {
        if (percent <= 5.0) return "ELITE";
        if (percent <= 10.0) return "HIGH";
        if (percent <= 15.0) return "MEDIUM";
        return "LOW";
    }

    /**
     * STORY and FEATURE are both treated as developer-assignable, story-point-bearing
     * deliverable work for this dashboard's velocity/cycle-time metrics — see
     * GetTeamDashboardUseCase.isStoryTierWork for the same reasoning. DORA's changeFailureRate
     * intentionally continues to check BUG only and is unaffected by this grouping.
     */

    private Double averageNonNull(List<Double> values) {
        List<Double> nonNull = values.stream().filter(v -> v != null).toList();
        if (nonNull.isEmpty()) return null;
        return nonNull.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
