package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.FieldMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Backlog;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Calibration;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.ComplexityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Config;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.EffortLevel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.EffortRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.EpicRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.FeatureRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.ForgottenItem;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.ManualOverride;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.PriorityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.Progress;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.StaleBand;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectRoadmapV2Output.TagShare;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PersonInfo;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.DayRange;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver.EffortScope;
import andromeda.sei.ms.feature.workitem.domain.EffortBearingLevel;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Project roadmap and backlog v2 (dashboards-customizaveis D1, {@code buildRoadmap}) of one
 * project and period: progress of epics and features by the work item hierarchy
 * ({@code parentExternalId}) with a forecast from the last {@value #PACE_WEEKS} weeks' pace, backlog
 * health, tags, cycle time by size and complexity, and manual overrides. Effort is the effective
 * effort of {@link EffortBearingLevelResolver}. One read of the project's items and sprints.
 */
@Service
public class GetProjectRoadmapV2UseCase {

    static final int PACE_WEEKS = 8;
    private static final int TOP_LIMIT = 8;
    private static final int OVERRIDES_LIMIT = 20;
    private static final double HOURS_PER_DAY = 24.0;
    private static final long STALE_DAYS = 60;
    private static final List<Integer> PRIORITIES = List.of(1, 2, 3, 4);
    private static final String EFFORT_LABEL = "Esforço";
    private static final String COMPLEXITY_LABEL = "Complexidade";
    private static final String SP_SUFFIX = " SP";
    private static final List<StaleBandSpec> STALE_BANDS = List.of(new StaleBandSpec("até 30 dias", 0, 30),
            new StaleBandSpec("30–60 dias", 30, 60), new StaleBandSpec("60–90 dias", 60, 90),
            new StaleBandSpec("mais de 90 dias", 90, null));

    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final EffortBearingLevelResolver effortResolver;
    private final AzureProjectBindingRepository bindingRepository;
    private final DashboardPeopleDirectory peopleDirectory;
    private final Clock clock;

    @Autowired
    public GetProjectRoadmapV2UseCase(ProjectRepository projectRepository, WorkItemRepository workItemRepository,
                                      SprintRepository sprintRepository, EffortBearingLevelResolver effortResolver,
                                      AzureProjectBindingRepository bindingRepository,
                                      DashboardPeopleDirectory peopleDirectory) {
        this(projectRepository, workItemRepository, sprintRepository, effortResolver, bindingRepository,
                peopleDirectory, Clock.systemUTC());
    }

    GetProjectRoadmapV2UseCase(ProjectRepository projectRepository, WorkItemRepository workItemRepository,
                               SprintRepository sprintRepository, EffortBearingLevelResolver effortResolver,
                               AzureProjectBindingRepository bindingRepository, DashboardPeopleDirectory peopleDirectory,
                               Clock clock) {
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.sprintRepository = sprintRepository;
        this.effortResolver = effortResolver;
        this.bindingRepository = bindingRepository;
        this.peopleDirectory = peopleDirectory;
        this.clock = clock;
    }

    /** @param periodDays one of 30, 90, 180, 365 ({@code DashboardPeriodInvalidException} otherwise) */
    public ProjectRoadmapV2Output execute(String orgId, String projectId, int periodDays) {
        Objects.requireNonNull(orgId, "orgId");
        projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Instant now = clock.instant();
        DashboardPeriod period = DashboardPeriod.of(periodDays, SprintWindow.toDay(now));
        List<Sprint> sprints = sprintRepository.findAllByOrganizationIdAndProjectId(orgId, projectId);
        ProjectPeriodItems items = ProjectPeriodItems.of(
                workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId), sprints, effortResolver);
        Context ctx = new Context(period.current(), now, items, items.effort().scope(), childrenIndex(items.all()));
        List<FeatureRow> features = features(ctx);
        return new ProjectRoadmapV2Output(
                new ProjectRoadmapV2Output.Period(periodDays, period.current().first().toString(),
                        period.current().last().toString()),
                config(orgId, projectId, items), epics(ctx, features), features, backlog(ctx, futureSprintIds(sprints)),
                tagShares(ctx, items.doneUnitsIn(ctx.range())), calibration(ctx, peopleDirectory.people(orgId)), now);
    }

    private static Map<String, List<WorkItem>> childrenIndex(List<WorkItem> items) {
        return items.stream().filter(i -> i.getParentExternalId() != null && !ProjectPeriodItems.isContainer(i))
                .collect(Collectors.groupingBy(WorkItem::getParentExternalId));
    }

    private Config config(String orgId, String projectId, ProjectPeriodItems items) {
        String effortField = bindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(orgId, projectId)
                .map(GetProjectRoadmapV2UseCase::effortField).orElse(null);
        return new Config(effortField, new EffortLevel(EffortBearingLevel.AUTO.name(),
                effortResolver.resolve(null, items.all()).name()));
    }

    private static String effortField(AzureProjectBinding binding) {
        WorkflowConfig config = binding.getWorkflowConfig();
        if (config == null) return WorkflowConfig.DEFAULT_EFFORT_FIELD_REFERENCE_NAME;
        return config.resolveFieldMapping(FieldMapping.InternalFieldTarget.EFFORT)
                .map(FieldMapping::getAzureFieldReferenceName).orElse(null);
    }

    private static List<FeatureRow> features(Context ctx) {
        Map<String, String> epicTitles = nodes(ctx, WorkItem.ResolvedType.EPIC).stream()
                .collect(Collectors.toMap(WorkItem::getExternalId, i -> Objects.toString(i.getTitle(), ""), (a, b) -> a));
        return sortByActivity(nodes(ctx, WorkItem.ResolvedType.FEATURE).stream()
                .map(f -> new FeatureRow(f.getExternalId(), f.getTitle(), f.getParentExternalId(),
                        epicTitles.get(f.getParentExternalId()), status(f),
                        progress(ctx, ctx.children().getOrDefault(f.getExternalId(), List.of()))))
                .toList(), FeatureRow::status, r -> r.progress().lastActivityAt());
    }

    private static List<EpicRow> epics(Context ctx, List<FeatureRow> features) {
        return sortByActivity(nodes(ctx, WorkItem.ResolvedType.EPIC).stream().map(epic -> {
            List<FeatureRow> own = features.stream().filter(f -> epic.getExternalId().equals(f.parentExternalId())).toList();
            List<WorkItem> children = new ArrayList<>(ctx.children().getOrDefault(epic.getExternalId(), List.of()));
            own.forEach(f -> children.addAll(ctx.children().getOrDefault(f.externalId(), List.of())));
            return new EpicRow(epic.getExternalId(), epic.getTitle(), status(epic), tags(epic), own.size(),
                    (int) own.stream().filter(f -> isDone(f.status())).count(), progress(ctx, children));
        }).toList(), EpicRow::status, r -> r.progress().lastActivityAt());
    }

    private static List<WorkItem> nodes(Context ctx, WorkItem.ResolvedType type) {
        return filter(ctx.items().all(), i -> i.getResolvedType() == type && i.getExternalId() != null);
    }

    /** Not done first, then the most recent activity. */
    private static <T> List<T> sortByActivity(List<T> rows, Function<T, String> status, Function<T, Instant> activity) {
        return rows.stream().sorted(Comparator.comparing((T r) -> isDone(status.apply(r)))
                .thenComparing(activity, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private static Progress progress(Context ctx, List<WorkItem> children) {
        List<WorkItem> done = filter(children, GetProjectRoadmapV2UseCase::isDone);
        Double total = ctx.scope().totalEffort(children);
        Double doneEffort = total == null ? null : orZero(ctx.scope().totalEffort(done));
        Instant paceFrom = ctx.now().minus(Duration.ofDays(PACE_WEEKS * 7L));
        double recent = orZero(ctx.scope().totalEffort(filter(done, c -> c.getCompletedAt() != null
                && !c.getCompletedAt().isBefore(paceFrom) && !c.getCompletedAt().isAfter(ctx.now()))));
        Double pace = recent > 0 ? recent / PACE_WEEKS : null;
        return new Progress(total, doneEffort, total == null || total <= 0 ? null : doneEffort / total * 100.0,
                children.size(), done.size(),
                (int) children.stream().filter(c -> ctx.scope().effectiveEffort(c) == null).count(),
                total == null ? null : orZero(ctx.scope().totalEffort(filter(done, c -> ctx.range().contains(c.getCompletedAt())))),
                pace, forecast(total, doneEffort, pace), lastActivity(children));
    }

    private static Double forecast(Double total, Double done, Double pace) {
        if (total == null) return null;
        double remaining = total - done;
        if (remaining <= 0) return 0d;
        return pace == null ? null : remaining / pace;
    }

    private static Instant lastActivity(List<WorkItem> children) {
        return children.stream().map(GetProjectRoadmapV2UseCase::changedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    private static Instant changedAt(WorkItem item) {
        return item.getExternalUpdatedAt() != null ? item.getExternalUpdatedAt() : item.getCreatedAt();
    }

    private static Set<String> futureSprintIds(List<Sprint> sprints) {
        return sprints.stream().filter(s -> s.getTimeFrame() == Sprint.TimeFrame.FUTURE && s.getId() != null)
                .map(Sprint::getId).collect(Collectors.toSet());
    }

    private static Backlog backlog(Context ctx, Set<String> futureSprintIds) {
        List<WorkItem> backlog = filter(ctx.items().all(), i -> !ProjectPeriodItems.isContainer(i)
                && i.getResolvedStatus() != WorkItem.ResolvedStatus.DONE
                && (i.getSprintId() == null || futureSprintIds.contains(i.getSprintId())));
        List<WorkItem> dated = filter(backlog, i -> changedAt(i) != null);
        return new Backlog(backlog.size(), ctx.scope().totalEffort(backlog),
                Ratios.percent(backlog.stream().filter(i -> ctx.scope().effectiveEffort(i) == null).count(), backlog.size()),
                Ratios.percent(backlog.stream().filter(i -> i.getParentExternalId() == null).count(), backlog.size()),
                STALE_BANDS.stream().map(b -> new StaleBand(b.label(), b.minDays(), b.maxDays(),
                        (int) dated.stream().filter(i -> b.contains(ageDays(ctx, i))).count())).toList(),
                PRIORITIES.stream().map(p -> priorityRow(ctx, backlog, p)).toList(),
                dated.stream().sorted(Comparator.comparing(GetProjectRoadmapV2UseCase::changedAt)).limit(TOP_LIMIT)
                        .map(i -> forgotten(ctx, i)).toList(),
                tagShares(ctx, backlog));
    }

    private static PriorityRow priorityRow(Context ctx, List<WorkItem> backlog, int priority) {
        List<WorkItem> withPriority = filter(backlog, i -> Integer.valueOf(priority).equals(ProjectPeriodItems.priority(i)));
        return new PriorityRow(priority, withPriority.size(), (int) withPriority.stream()
                .filter(i -> changedAt(i) != null && ageDays(ctx, i) >= STALE_DAYS).count());
    }

    private static ForgottenItem forgotten(Context ctx, WorkItem item) {
        return new ForgottenItem(item.getExternalId(), item.getTitle(),
                item.getResolvedType() != null ? item.getResolvedType().name() : null, ProjectPeriodItems.priority(item),
                ctx.scope().effectiveEffort(item), ageDays(ctx, item), tags(item));
    }

    private static double ageDays(Context ctx, WorkItem item) {
        return Ratios.hours(changedAt(item), ctx.now()) / HOURS_PER_DAY;
    }


    /** Count and effective effort per tag, by effort then count, top {@value #TOP_LIMIT}. */
    private static List<TagShare> tagShares(Context ctx, Collection<WorkItem> items) {
        Map<String, double[]> byTag = new LinkedHashMap<>();
        for (WorkItem item : items) {
            double effort = orZero(ctx.scope().effectiveEffort(item));
            for (String tag : tags(item)) {
                double[] acc = byTag.computeIfAbsent(tag, t -> new double[2]);
                acc[0] += 1;
                acc[1] += effort;
            }
        }
        return byTag.entrySet().stream().map(e -> new TagShare(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingDouble(TagShare::sp).reversed().thenComparing(TagShare::count, Comparator.reverseOrder()))
                .limit(TOP_LIMIT).toList();
    }

    private static Calibration calibration(Context ctx, Map<String, PersonInfo> people) {
        List<WorkItem> done = ctx.items().doneUnitsIn(ctx.range());
        List<WorkItem> sprintUnits = ctx.items().sprintsOverlapping(ctx.range()).stream()
                .flatMap(f -> f.summary().units().stream()).toList();
        return new Calibration(byEffort(done), byComplexity(done), overrides(ctx, people),
                Ratios.percent(sprintUnits.stream().filter(GetProjectRoadmapV2UseCase::isOverridden).count(),
                        sprintUnits.size()));
    }

    private static List<EffortRow> byEffort(List<WorkItem> done) {
        return groupedBy(done, WorkItem::getEffort).entrySet().stream()
                .map(e -> new EffortRow(number(e.getKey()) + SP_SUFFIX, e.getKey(), e.getValue().size(),
                        Percentiles.of(e.getValue().stream().map(WorkItem::getCycleTimeHours).toList())))
                .toList();
    }

    private static List<ComplexityRow> byComplexity(List<WorkItem> done) {
        return groupedBy(done, WorkItem::getComplexity).entrySet().stream()
                .map(e -> new ComplexityRow(number(e.getKey()), e.getKey(), e.getValue().size(),
                        Percentiles.of(e.getValue().stream().map(WorkItem::getCycleTimeHours).toList()),
                        DashboardV2Views.averageOrNull(e.getValue().stream().map(WorkItem::getEffort).toList())))
                .toList();
    }

    /** Items by a value of their own field, ascending; items without it are left out. */
    private static Map<Double, List<WorkItem>> groupedBy(List<WorkItem> items, Function<WorkItem, Double> value) {
        return items.stream().filter(i -> value.apply(i) != null)
                .collect(Collectors.groupingBy(value, TreeMap::new, Collectors.toList()));
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static List<ManualOverride> overrides(Context ctx, Map<String, PersonInfo> people) {
        List<ManualOverride> rows = new ArrayList<>();
        for (WorkItem item : ctx.items().all()) {
            if (item.getEffortSource() == FieldValueSource.MANUAL_OVERRIDE && ctx.range().contains(item.getEffortOverriddenAt())) {
                rows.add(new ManualOverride(item.getExternalId(), item.getTitle(), EFFORT_LABEL, item.getEffort(),
                        name(people, item.getEffortOverriddenBy()), item.getEffortOverriddenAt()));
            }
            if (item.getComplexitySource() == FieldValueSource.MANUAL_OVERRIDE
                    && ctx.range().contains(item.getComplexityOverriddenAt())) {
                rows.add(new ManualOverride(item.getExternalId(), item.getTitle(), COMPLEXITY_LABEL, item.getComplexity(),
                        name(people, item.getComplexityOverriddenBy()), item.getComplexityOverriddenAt()));
            }
        }
        return rows.stream().sorted(Comparator.comparing(ManualOverride::at).reversed()).limit(OVERRIDES_LIMIT).toList();
    }

    private static String name(Map<String, PersonInfo> people, String userId) {
        if (userId == null) return null;
        PersonInfo person = people.get(userId);
        return person != null ? person.name() : userId;
    }

    private static boolean isOverridden(WorkItem item) {
        return item.getEffortSource() == FieldValueSource.MANUAL_OVERRIDE
                || item.getComplexitySource() == FieldValueSource.MANUAL_OVERRIDE;
    }

    private static boolean isDone(WorkItem item) {
        return item.getResolvedStatus() == WorkItem.ResolvedStatus.DONE;
    }

    private static boolean isDone(String status) {
        return WorkItem.ResolvedStatus.DONE.name().equals(status);
    }

    private static String status(WorkItem item) {
        return item.getResolvedStatus() != null ? item.getResolvedStatus().name() : null;
    }

    private static List<String> tags(WorkItem item) {
        return item.getLabels() == null ? List.of() : item.getLabels();
    }

    private static double orZero(Double value) {
        return value == null ? 0d : value;
    }

    private static <T> List<T> filter(Collection<T> values, Predicate<T> predicate) {
        return values.stream().filter(predicate).toList();
    }

    /** A staleness band {@code [minDays, maxDays)}; {@code maxDays} {@code null} = open-ended. */
    private record StaleBandSpec(String label, int minDays, Integer maxDays) {
        boolean contains(double ageDays) {
            return ageDays >= minDays && (maxDays == null || ageDays < maxDays);
        }
    }

    private record Context(DayRange range, Instant now, ProjectPeriodItems items, EffortScope scope,
                           Map<String, List<WorkItem>> children) {}
}
