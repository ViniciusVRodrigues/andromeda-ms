package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.CompletedWorkItem;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMethod;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.metrics.domain.PerformanceClass;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Strategic dashboard (strategic-dashboard, metrics-correctness).
 *
 * <ul>
 *   <li>Velocity follows the single definition (resolver + effective effort) — the legacy
 *       {@code storyPoints} field is no longer read. Durations are p50/p90.</li>
 *   <li>Delivery metrics come from {@link DeliveryMetricsService}: per project, and at organization
 *       level over the union of the projects' merged pull requests and completed items.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GetStrategicDashboardUseCase {

    private static final long DEFAULT_RANGE_DAYS = 90;
    private static final String LEVEL_ORG = "ORG";
    private static final String LEVEL_PROJECT = "PROJECT";
    private static final String LEVEL_MEMBER = "MEMBER";

    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final EffortBearingLevelResolver effortBearingLevelResolver;
    private final UserRepository userRepository;
    private final GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;
    private final DeliveryMetricsService deliveryMetricsService;

    /**
     * @param visibleProjectIds projects where the caller effectively holds {@code DASHBOARD_STRATEGIC}
     *                          (project-scoped-permissions 7.2): level 1 aggregates only these; levels
     *                          2 and 3 were already checked on their project by {@code @perm}
     */
    public StrategicDashboardOutput execute(String orgId, String authUserId,
                                             String projectId, String memberId,
                                             LocalDate startDate, LocalDate endDate,
                                             Set<String> visibleProjectIds) {
        LocalDate from = startDate != null ? startDate : LocalDate.now().minusDays(DEFAULT_RANGE_DAYS);
        LocalDate to = endDate != null ? endDate : LocalDate.now();

        if (projectId != null && memberId != null) {
            DeveloperDashboardOutput devOutput = getDeveloperDashboardUseCase.execute(
                    orgId, memberId, projectId, null, from, to, null);
            return new StrategicDashboardOutput(LEVEL_MEMBER, null, null, devOutput, null);
        }
        Window window = new Window(from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        return projectId != null ? levelTwo(orgId, projectId, window) : levelOne(orgId, window, visibleProjectIds);
    }

    private StrategicDashboardOutput levelOne(String orgId, Window window, Set<String> visibleProjectIds) {
        List<ProjectData> data = projectRepository.findAllActiveByOrganizationId(orgId).stream()
                .filter(project -> visibleProjectIds.contains(project.getId()))
                .map(project -> load(orgId, project, window))
                .toList();

        List<StrategicDashboardOutput.ProjectSummary> summaries = data.stream()
                .map(d -> buildProjectSummary(orgId, d, window))
                .toList();

        List<MergedChange> allChanges = DeliveryMetricsService.union(
                data.stream().map(ProjectData::changes).toList(), MergedChange::id);
        List<CompletedWorkItem> allItems = DeliveryMetricsService.union(
                data.stream().map(ProjectData::completedItems).toList(), CompletedWorkItem::id);
        DeliveryMetrics orgDelivery = deliveryMetricsService.calculate(orgId, allChanges, allItems,
                window.from(), window.to());

        return new StrategicDashboardOutput(LEVEL_ORG, summaries, null, null, orgDelivery);
    }

    private StrategicDashboardOutput.ProjectSummary buildProjectSummary(String orgId, ProjectData data, Window window) {
        List<WorkItem> doneUnits = data.doneUnitsInWindow();
        Double delivered = data.effort().scope().totalEffort(doneUnits);
        DeliveryMetrics delivery = deliveryMetricsService.calculate(orgId, data.changes(), data.completedItems(),
                window.from(), window.to());
        return new StrategicDashboardOutput.ProjectSummary(
                data.project().getId(), data.project().getName(), data.completedInWindow().size(),
                delivered == null ? 0d : delivered,
                average(doneUnits, WorkItem::getCycleTimeHours), average(doneUnits, WorkItem::getLeadTimeHours),
                toDora(delivery),
                Percentiles.of(doneUnits.stream().map(WorkItem::getCycleTimeHours).toList()),
                Percentiles.of(doneUnits.stream().map(WorkItem::getLeadTimeHours).toList()),
                delivery);
    }

    private StrategicDashboardOutput levelTwo(String orgId, String projectId, Window window) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> orgId.equals(p.getOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project not found: " + projectId));
        ProjectData data = load(orgId, project, window);

        List<User> users = userRepository.findAllByOrganizationId(orgId);
        Map<String, User> userById = users.stream().filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));

        Map<String, List<WorkItem>> completedByAssignee = data.completedInWindow().stream()
                .filter(wi -> wi.getAssigneeUserId() != null)
                .collect(Collectors.groupingBy(WorkItem::getAssigneeUserId));
        List<StrategicDashboardOutput.MemberSummary> members = completedByAssignee.entrySet().stream()
                .map(e -> buildMemberSummary(e.getKey(), e.getValue(), data, userById.get(e.getKey())))
                .toList();

        DeliveryMetrics delivery = deliveryMetricsService.calculate(orgId, data.changes(), data.completedItems(),
                window.from(), window.to());
        StrategicDashboardOutput.ProjectDetail detail = new StrategicDashboardOutput.ProjectDetail(
                project.getId(), project.getName(), members, toDora(delivery), delivery);
        return new StrategicDashboardOutput(LEVEL_PROJECT, null, detail, null, delivery);
    }

    private StrategicDashboardOutput.MemberSummary buildMemberSummary(String userId, List<WorkItem> completed,
                                                                       ProjectData data, User user) {
        List<WorkItem> doneUnits = data.doneUnitsInWindow().stream()
                .filter(wi -> userId.equals(wi.getAssigneeUserId()))
                .toList();
        Double delivered = data.effort().scope().totalEffort(doneUnits);
        return new StrategicDashboardOutput.MemberSummary(
                userId, displayName(user, userId), user != null && user.getAzureId() != null,
                delivered == null ? 0d : delivered,
                average(doneUnits, WorkItem::getCycleTimeHours), average(doneUnits, WorkItem::getLeadTimeHours),
                completed.size(),
                Percentiles.of(doneUnits.stream().map(WorkItem::getCycleTimeHours).toList()),
                Percentiles.of(doneUnits.stream().map(WorkItem::getLeadTimeHours).toList()));
    }

    /**
     * Everything the dashboard needs of one project, loaded once: all active items (the effort
     * level and the children index need the whole hierarchy, not only what completed in the
     * window), merged pull requests of its bound repositories and completed items of the window.
     */
    private ProjectData load(String orgId, Project project, Window window) {
        List<WorkItem> items = workItemRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId())
                .stream().filter(WorkItem::isActive).toList();
        EffortBearingLevelResolver.EffortSummary effort = effortBearingLevelResolver.summarize(items);
        List<WorkItem> completedInWindow = items.stream().filter(wi -> window.contains(wi.getCompletedAt())).toList();
        List<WorkItem> doneUnitsInWindow = effort.doneUnits().stream()
                .filter(wi -> window.contains(wi.getCompletedAt()))
                .toList();
        List<MergedChange> changes = deliveryMetricsService.mergedChangesOfProject(
                orgId, project.getId(), window.from(), window.to());
        return new ProjectData(project, effort, completedInWindow, doneUnitsInWindow, changes,
                DeliveryMetricsService.completedItems(completedInWindow));
    }

    /** Deprecated {@code dora} block, derived from the new calculator for one release. */
    static StrategicDashboardOutput.DoraMetrics toDora(DeliveryMetrics delivery) {
        return new StrategicDashboardOutput.DoraMetrics(
                delivery.changeLeadTime().p50(), name(delivery.changeLeadTimeClass()),
                delivery.deploymentsPerWeek(), name(delivery.deploymentFrequencyClass()),
                delivery.changeFailRatePercent(), name(delivery.changeFailRateClass()),
                null, delivery.method() != DeliveryMethod.VCS_PROXY);
    }

    private static String name(PerformanceClass performanceClass) {
        return performanceClass == null ? null : performanceClass.name();
    }

    private static String displayName(User user, String fallback) {
        if (user == null) return fallback;
        if (user.getName() != null && !user.getName().isBlank()) return user.getName();
        if (user.getEmail() != null && !user.getEmail().isBlank()) return user.getEmail();
        return fallback;
    }

    /** Deprecated average, kept for one release next to the percentiles. */
    private static Double average(List<WorkItem> items, Function<WorkItem, Double> metric) {
        OptionalDouble avg = items.stream().map(metric).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    private record ProjectData(Project project, EffortBearingLevelResolver.EffortSummary effort,
                               List<WorkItem> completedInWindow, List<WorkItem> doneUnitsInWindow,
                               List<MergedChange> changes, List<CompletedWorkItem> completedItems) {}

    /** Half-open {@code [from, to)}: the requested dates, inclusive, as instants. */
    private record Window(Instant from, Instant to) {
        boolean contains(Instant at) {
            return at != null && !at.isBefore(from) && at.isBefore(to);
        }
    }
}
