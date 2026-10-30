package andromeda.sei.ms.feature.dashboard;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocumentMapper;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageMongoRepository;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageRepositoryImpl;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocumentMapper;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationMongoRepository;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationRepositoryImpl;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetOrganizationDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectOverviewV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectRoadmapV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.InMemoryVcsStore;
import andromeda.sei.ms.feature.vcs.application.VcsReadService;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PERF2 / P0 - golden-file parity of the AI figures of the v2 dashboards (organization, project overview,
 * team, project roadmap) on a real Mongo (Testcontainers) with the real {@link AiUsageRepositoryImpl},
 * {@link AiUsageAllocationRepositoryImpl} and {@link ProjectAiCostCalculator}. The golden files in
 * {@code src/test/resources/perf2-parity/} were recorded with the code BEFORE the sums moved to the database;
 * the refactoring must keep them byte-for-byte equal in value (numbers compare by value, not by scale).
 *
 * <p>Version control is deliberately empty (no repositories, bindings, pull requests or commits), so the
 * golden does not depend on the VCS metrics, which change in their own change.
 *
 * <p>Record: {@code ./gradlew test --tests "*DashboardAiParityTest" -Dperf2.parity.record=true}.
 *
 * <p>Now = 2026-10-15 12:00Z. Data: 8 people, 3 projects, ~800 days, seeded {@code new Random(2610L)}.
 */
@MongoIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardAiParityTest {

    private static final String ORG = "org-perf2";
    private static final String OTHER = "org-outra";
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-15");
    private static final int DAYS_BACK = 800;
    private static final String RECORD_PROPERTY = "perf2.parity.record";
    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "perf2-parity");

    private static final String[] MODELS = {"claude-opus-5", "claude-sonnet-5", "gpt-5", null};
    private static final String[] EDITORS = {"vscode", "cursor", "jetbrains", null};
    private static final List<String> PEOPLE = List.of("user-1", "user-2", "user-3", "user-4", "user-5", "user-6",
            "user-7", "user-8");
    private static final String P1 = "proj-1";
    private static final String P2 = "proj-2";
    private static final String P3 = "proj-3";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AiUsageMongoRepository aiUsageMongo;
    @Autowired private AiUsageAllocationMongoRepository allocationMongo;

    private AiUsageRepositoryImpl aiUsageRepository;
    private AiUsageAllocationRepositoryImpl allocationRepository;

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ConnectorTokenRepository connectorTokenRepository = mock(ConnectorTokenRepository.class);
    private final SprintRepository sprintRepository = mock(SprintRepository.class);
    private final WorkItemRepository workItemRepository = mock(WorkItemRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final AzureConnectionRepository azureConnectionRepository = mock(AzureConnectionRepository.class);
    private final GithubConnectionRepository githubConnectionRepository = mock(GithubConnectionRepository.class);
    private final AzureProjectBindingRepository azureBindingRepository = mock(AzureProjectBindingRepository.class);
    private final IntegrationSyncLogRepository syncLogRepository = mock(IntegrationSyncLogRepository.class);

    // The generated data (kept to prove the edge cases are exercised).
    private final List<AIUsage> usages = new ArrayList<>();
    private final List<AiUsageAllocation> allocations = new ArrayList<>();
    private final Map<String, List<WorkItem>> itemsByProject = new LinkedHashMap<>();
    private final Map<String, Object> outputs = new LinkedHashMap<>();
    private final Map<String, Supplier<Object>> cases = new LinkedHashMap<>();

    private VcsReadService read;
    private DeliveryMetricsService delivery;

    @BeforeAll
    void seed() {
        TenantContextHolder.set(ORG);
        AiUsageDocumentMapper usageMapper = new AiUsageDocumentMapper();
        AiUsageAllocationDocumentMapper allocationMapper = new AiUsageAllocationDocumentMapper();
        aiUsageRepository = new AiUsageRepositoryImpl(aiUsageMongo, usageMapper, mongoTemplate);
        allocationRepository = new AiUsageAllocationRepositoryImpl(allocationMongo, allocationMapper, mongoTemplate);
        clearCollections();

        List<User> users = new ArrayList<>();
        for (int i = 0; i < PEOPLE.size(); i++) {
            users.add(new User(PEOPLE.get(i), ORG, PEOPLE.get(i) + "@x.io", null, "Pessoa " + (i + 1), "role", null,
                    null, null, null, null, true, i != 7, 0L, NOW, NOW));
        }
        // u3 is in P1 and P2, u7 in no project, u8 (inactive) in P3.
        List<Project> projects = List.of(
                project(P1, "Orion", "orion", List.of("user-1", "user-2", "user-3")),
                project(P2, "Bootes", "bootes", List.of("user-3", "user-4", "user-5")),
                project(P3, "Cygnus", "cygnus", List.of("user-6", "user-8")));
        Random rnd = new Random(2610L);
        Map<String, List<Sprint>> sprints = new LinkedHashMap<>();
        sprints.put(P1, sprintsOf(P1, "2026-09-01", "2026-10-21"));
        sprints.put(P2, sprintsOf(P2, "2026-10-08", "2026-10-21"));
        sprints.put(P3, sprintsOf(P3, "2026-10-01", "2026-10-28"));
        for (Project p : projects) {
            itemsByProject.put(p.getId(), workItemsOf(rnd, p, sprints.get(p.getId())));
        }
        generate(rnd);
        insertAll(usageMapper, allocationMapper);
        stubRepositories(users, projects, sprints);
        buildCases();
    }

    @AfterAll
    void cleanUp() {
        clearCollections();
        TenantContextHolder.clear();
    }

    private void clearCollections() {
        mongoTemplate.remove(new Query(), "ai_usages");
        mongoTemplate.remove(new Query(), "ai_usage_allocations");
    }

    // ------------------------------------------------------------------
    // Fixture: projects, sprints, work items
    // ------------------------------------------------------------------

    private static Project project(String id, String name, String slug, List<String> members) {
        return new Project(id, ORG, name, slug, null, true, members, 0L, NOW, NOW);
    }

    private static Instant at(String day) {
        return Instant.parse(day + "T00:00:00Z");
    }

    /** Twelve 14-day past sprints before the current one (which may be long), plus a future one. */
    private static List<Sprint> sprintsOf(String projectId, String curStart, String curEnd) {
        List<Sprint> list = new ArrayList<>();
        String n = projectId.substring(projectId.length() - 1);
        list.add(sprint("s-" + n + "-cur", projectId, "Sprint atual " + n, Sprint.TimeFrame.CURRENT, curStart, curEnd));
        LocalDate end = LocalDate.parse(curStart).minusDays(1);
        for (int i = 1; i <= 12; i++) {
            LocalDate start = end.minusDays(13);
            list.add(sprint("s-" + n + "-p" + i, projectId, "Sprint passada " + n + "." + i, Sprint.TimeFrame.PAST,
                    start.toString(), end.toString()));
            end = start.minusDays(1);
        }
        list.add(sprint("s-" + n + "-fut", projectId, "Sprint futura " + n, Sprint.TimeFrame.FUTURE, "2026-10-29",
                "2026-11-11"));
        return list;
    }

    private static Sprint sprint(String id, String projectId, String name, Sprint.TimeFrame frame, String start,
                                 String end) {
        return new Sprint(id, ORG, projectId, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-" + projectId,
                "Time " + projectId, name, "\\" + projectId + "\\" + name, at(start), at(end), frame, true, 0L,
                Instant.parse("2025-01-01T00:00:00Z"), null);
    }

    private List<WorkItem> workItemsOf(Random rnd, Project project, List<Sprint> sprints) {
        String n = project.getId().substring(project.getId().length() - 1);
        List<WorkItem> items = new ArrayList<>();
        items.add(item("wi-" + n + "-epic", project, "p" + n + "-e1", WorkItem.ResolvedType.EPIC,
                WorkItem.ResolvedStatus.IN_PROGRESS, null, null, null, null, null));
        items.add(item("wi-" + n + "-feat", project, "p" + n + "-f1", WorkItem.ResolvedType.FEATURE,
                WorkItem.ResolvedStatus.IN_PROGRESS, "p" + n + "-e1", null, null, null, null));
        WorkItem.ResolvedType[] types = {WorkItem.ResolvedType.STORY, WorkItem.ResolvedType.TASK,
                WorkItem.ResolvedType.BUG, WorkItem.ResolvedType.TASK};
        for (int i = 1; i <= 14; i++) {
            WorkItem.ResolvedType type = types[rnd.nextInt(types.length)];
            double r = rnd.nextDouble();
            WorkItem.ResolvedStatus status = r < 0.6 ? WorkItem.ResolvedStatus.DONE
                    : r < 0.8 ? WorkItem.ResolvedStatus.IN_PROGRESS : WorkItem.ResolvedStatus.TODO;
            Sprint sprint = rnd.nextDouble() < 0.85 ? sprints.get(rnd.nextInt(sprints.size())) : null;
            Instant created = NOW.minusSeconds(86400L * (20 + rnd.nextInt(380)));
            Instant started = status == WorkItem.ResolvedStatus.TODO ? null : created.plusSeconds(3600L * rnd.nextInt(200));
            Instant completed = null;
            if (status == WorkItem.ResolvedStatus.DONE) {
                Instant base = sprint != null && sprint.getTimeFrame() != Sprint.TimeFrame.FUTURE
                        ? sprint.getStartDate().plusSeconds(3600L * rnd.nextInt(24 * 12))
                        : NOW.minusSeconds(86400L * rnd.nextInt(400));
                completed = base.isAfter(NOW) ? NOW.minusSeconds(3600L * (1 + rnd.nextInt(48))) : base;
                if (started == null || started.isAfter(completed)) started = completed.minusSeconds(3600L * 30);
            }
            String assignee = project.getMemberIds().get(rnd.nextInt(project.getMemberIds().size()));
            WorkItem w = item("wi-" + n + "-" + i, project, "p" + n + "-i" + i, type, status,
                    rnd.nextDouble() < 0.8 ? "p" + n + "-f1" : null, sprint == null ? null : sprint.getId(), assignee,
                    started, completed);
            w.setCreatedAt(created);
            w.setEffort((double) (1 + rnd.nextInt(8)));
            if (type == WorkItem.ResolvedType.STORY) w.setComplexity((double) (1 + rnd.nextInt(8)));
            w.setPriority(String.valueOf(1 + rnd.nextInt(4)));
            items.add(w);
        }
        return items;
    }

    private static WorkItem item(String id, Project project, String externalId, WorkItem.ResolvedType type,
                                 WorkItem.ResolvedStatus status, String parent, String sprintId, String assignee,
                                 Instant started, Instant completed) {
        WorkItem w = new WorkItem(id, ORG, project.getId(), WorkItem.Source.AZURE_DEVOPS, externalId, NOW.minusSeconds(86400),
                "Item " + externalId, type.name(), type, status.name(), status, null, List.of(), sprintId, null,
                assignee, null, parent, NOW.minusSeconds(86400L * 100), started, completed, null, null, true, 0L, NOW,
                null, null, null, null, null, null, null, null);
        return w;
    }

    // ------------------------------------------------------------------
    // Fixture: AI usage and allocations
    // ------------------------------------------------------------------

    private static String hex(long base, long seq) {
        return String.format("%024x", base + seq);
    }

    private static final long USAGE_BASE = 0x600000000000L;
    private static final long OTHER_USAGE_BASE = 0x610000000000L;
    private static final long ALLOC_BASE = 0x700000000000L;
    private static final long OTHER_ALLOC_BASE = 0x710000000000L;
    private static final long MISSING_BASE = 0x900000000000L;

    private void generate(Random rnd) {
        List<String> allItemIds = new ArrayList<>();
        itemsByProject.values().forEach(l -> l.forEach(i -> allItemIds.add(i.getId())));
        long usageSeq = 0, otherSeq = 0, allocSeq = 0, otherAllocSeq = 0, missingSeq = 0;

        // The other organization: same user ids, its own records and a few allocations splitting to our item ids.
        List<String> otherIds = new ArrayList<>();
        for (int d = DAYS_BACK; d >= 0; d--) {
            LocalDate day = TODAY.minusDays(d);
            for (String user : PEOPLE) {
                if (rnd.nextDouble() >= 0.3) continue;
                int count = 1 + rnd.nextInt(3);
                List<AIUsage> dayRecords = new ArrayList<>();
                for (int k = 0; k < count; k++) {
                    AIUsage u = record(rnd, OTHER, hex(OTHER_USAGE_BASE, otherSeq++), user, day, k);
                    dayRecords.add(u);
                    otherIds.add(u.getId());
                }
                if (rnd.nextDouble() < 0.5) {
                    String allocId = hex(OTHER_ALLOC_BASE, otherAllocSeq++);
                    List<AllocationSplit> splits = List.of(new AllocationSplit(allItemIds.get(rnd.nextInt(allItemIds.size())),
                            null, new BigDecimal("0.80")));
                    allocations.add(new AiUsageAllocation(allocId, OTHER, user, day,
                            dayRecords.stream().map(AIUsage::getId).toList(), splits,
                            AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, null, NOW, NOW));
                    dayRecords.forEach(r -> r.setAllocationId(allocId));
                }
                usages.addAll(dayRecords);
            }
        }

        List<String> anyInactive = new ArrayList<>();
        String previousAllocationId = null;
        for (int d = DAYS_BACK; d >= 0; d--) {
            LocalDate day = TODAY.minusDays(d);
            for (String user : PEOPLE) {
                if (rnd.nextDouble() >= 0.5) continue;
                int count = 1 + rnd.nextInt(4);
                List<AIUsage> dayRecords = new ArrayList<>();
                for (int k = 0; k < count; k++) {
                    AIUsage u = record(rnd, ORG, hex(USAGE_BASE, usageSeq++), user, day, k);
                    u.setActive(rnd.nextDouble() >= 0.05);
                    if (!u.isActive()) anyInactive.add(u.getId());
                    dayRecords.add(u);
                }
                usages.addAll(dayRecords);
                if (rnd.nextDouble() >= 0.6) continue;

                double roll = rnd.nextDouble();
                AiUsageAllocation.Status status = roll < 0.86 && roll >= 0.72 ? AiUsageAllocation.Status.PENDING_VALIDATION
                        : AiUsageAllocation.Status.VALIDATED;
                boolean allocActive = roll < 0.86; // [0.86, 1): VALIDATED but inactive
                String allocId = hex(ALLOC_BASE, allocSeq++);
                List<AllocationSplit> splits = splits(rnd, user, allItemIds, missingSeq++);

                List<String> ids = new ArrayList<>();
                double special = rnd.nextDouble();
                List<AIUsage> active = dayRecords.stream().filter(AIUsage::isActive).toList();
                List<AIUsage> inactive = dayRecords.stream().filter(r -> !r.isActive()).toList();
                if (status == AiUsageAllocation.Status.VALIDATED && special < 0.05) {
                    // validated with no records
                } else if (status == AiUsageAllocation.Status.VALIDATED && special < 0.08) {
                    ids.add(hex(MISSING_BASE, missingSeq++));
                    if (rnd.nextBoolean()) ids.add(hex(MISSING_BASE, missingSeq++));
                } else {
                    for (AIUsage r : active) if (rnd.nextDouble() < 0.8) ids.add(r.getId());
                    if (ids.isEmpty() && !active.isEmpty()) ids.add(active.get(0).getId());
                    if (!ids.isEmpty() && rnd.nextDouble() < 0.08) ids.add(ids.get(0));
                    if (rnd.nextDouble() < 0.10) {
                        if (!inactive.isEmpty()) ids.add(inactive.get(0).getId());
                        else if (!anyInactive.isEmpty()) ids.add(anyInactive.get(rnd.nextInt(anyInactive.size())));
                    }
                    if (rnd.nextDouble() < 0.06) ids.add(otherIds.get(rnd.nextInt(otherIds.size())));
                    if (rnd.nextDouble() < 0.06) ids.add(hex(MISSING_BASE, missingSeq++));
                    Collections.shuffle(ids, rnd);
                }
                AiUsageAllocation allocation = new AiUsageAllocation(allocId, ORG, user, day, ids, splits, status,
                        AiUsageAllocation.AssignedBy.MANUAL, allocActive, null, NOW, NOW);
                allocations.add(allocation);

                // Stamp allocationId on the covered records, deliberately inconsistent in places.
                Set<String> covered = new HashSet<>(ids);
                for (AIUsage r : dayRecords) {
                    boolean in = covered.contains(r.getId());
                    double s = rnd.nextDouble();
                    if (in && status == AiUsageAllocation.Status.VALIDATED && allocActive) {
                        if (s < 0.05) r.setAllocationId(null);
                        else if (s < 0.10 && previousAllocationId != null) r.setAllocationId(previousAllocationId);
                        else r.setAllocationId(allocId);
                    } else if (s < 0.10) {
                        r.setAllocationId(allocId); // legacy: pointing at a pending / inactive / not-covering allocation
                    }
                }
                if (status == AiUsageAllocation.Status.VALIDATED && allocActive) previousAllocationId = allocId;
            }
        }
    }

    private List<AllocationSplit> splits(Random rnd, String user, List<String> allItemIds, long unknownSeq) {
        int n = 1 + rnd.nextInt(3);
        int total = 60 + rnd.nextInt(41);
        List<String> own = new ArrayList<>();
        itemsByProject.forEach((projectId, items) -> {
            if (membersOf(projectId).contains(user)) items.forEach(i -> own.add(i.getId()));
        });
        List<AllocationSplit> splits = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int remaining = total;
        for (int k = 0; k < n; k++) {
            int part = k == n - 1 ? remaining : 1 + rnd.nextInt(remaining - (n - 1 - k));
            remaining -= part;
            double pick = rnd.nextDouble();
            String id;
            if (pick < 0.10) id = "wi-unknown-" + unknownSeq + "-" + k;
            else if (pick < 0.65 && !own.isEmpty()) id = own.get(rnd.nextInt(own.size()));
            else id = allItemIds.get(rnd.nextInt(allItemIds.size()));
            if (!used.add(id)) continue;
            splits.add(new AllocationSplit(id, "t", BigDecimal.valueOf(part, 2)));
        }
        if (splits.isEmpty()) splits.add(new AllocationSplit(allItemIds.get(0), "t", new BigDecimal("0.50")));
        return splits;
    }

    private List<String> membersOf(String projectId) {
        return switch (projectId) {
            case P1 -> List.of("user-1", "user-2", "user-3");
            case P2 -> List.of("user-3", "user-4", "user-5");
            default -> List.of("user-6", "user-8");
        };
    }

    private static AIUsage record(Random rnd, String org, String id, String user, LocalDate day, int k) {
        String model = MODELS[rnd.nextInt(MODELS.length)];
        String editor = EDITORS[rnd.nextInt(EDITORS.length)];
        BigDecimal cost = rnd.nextDouble() < 0.10 ? null : BigDecimal.valueOf(rnd.nextInt(5_000_000), 6);
        long in = rnd.nextInt(50_000);
        long out = rnd.nextInt(20_000);
        int interactions = 1 + rnd.nextInt(300);
        Instant uploaded = day.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3600L * 20 + rnd.nextInt(7200));
        return new AIUsage(id, org, user, null, "dataset", day, model, null, null, "m" + k, null, in, out, interactions,
                editor, null, cost, uploaded, true, null, uploaded, uploaded);
    }

    private void insertAll(AiUsageDocumentMapper usageMapper, AiUsageAllocationDocumentMapper allocationMapper) {
        for (int from = 0; from < usages.size(); from += 1000) {
            mongoTemplate.insert(usages.subList(from, Math.min(usages.size(), from + 1000)).stream()
                    .map(usageMapper::toDocument).toList(), "ai_usages");
        }
        for (int from = 0; from < allocations.size(); from += 1000) {
            mongoTemplate.insert(allocations.subList(from, Math.min(allocations.size(), from + 1000)).stream()
                    .map(allocationMapper::toDocument).toList(), "ai_usage_allocations");
        }
    }

    // ------------------------------------------------------------------
    // Mocks and use cases
    // ------------------------------------------------------------------

    private void stubRepositories(List<User> users, List<Project> projects, Map<String, List<Sprint>> sprints) {
        for (Project p : projects) {
            when(projectRepository.findActiveByIdAndOrganizationId(p.getId(), ORG)).thenReturn(java.util.Optional.of(p));
            List<WorkItem> items = itemsByProject.get(p.getId());
            when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, p.getId())).thenReturn(items);
            when(workItemRepository.findMetricFieldsByOrganizationIdAndProjectId(ORG, p.getId())).thenReturn(items);
            when(workItemRepository.findActiveIdsByOrganizationIdAndProjectId(ORG, p.getId()))
                    .thenReturn(items.stream().map(WorkItem::getId).collect(java.util.stream.Collectors.toSet()));
            when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, p.getId())).thenReturn(sprints.get(p.getId()));
            sprints.get(p.getId()).forEach(s -> when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue(s.getId(), ORG))
                    .thenReturn(java.util.Optional.of(s)));
        }
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(projects);
        when(sprintRepository.findAllByOrganizationId(ORG)).thenReturn(sprints.values().stream().flatMap(List::stream).toList());
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(users);
        for (User u : users) when(userRepository.findByIdAndOrganizationId(u.getId(), ORG)).thenReturn(java.util.Optional.of(u));
        when(organizationRepository.findById(anyString())).thenReturn(java.util.Optional.empty());
        read = new VcsReadService(emptyVcs.bindingRepository, emptyVcs.repositoryRepository, emptyVcs.pullRequestRepository,
                emptyVcs.reviewEventRepository);
        delivery = new DeliveryMetricsService(read, workItemRepository, organizationRepository);
    }

    /** No repositories, bindings, pull requests or commits: the golden has no VCS figures. */
    private final InMemoryVcsStore emptyVcs = new InMemoryVcsStore();

    private ProjectAiCostCalculator calculator() {
        return new ProjectAiCostCalculator(allocationRepository, aiUsageRepository);
    }

    private DashboardPeopleDirectory peopleDirectory() {
        return new DashboardPeopleDirectory(userRepository, connectorTokenRepository);
    }

    private static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private GetOrganizationDashboardV2UseCase organizationUseCase() {
        OrganizationDashboardData data = new OrganizationDashboardData(projectRepository, userRepository,
                workItemRepository, sprintRepository, new EffortBearingLevelResolver(), emptyVcs.bindingRepository,
                emptyVcs.repositoryRepository, read, emptyVcs.commitRepository, delivery, organizationRepository,
                aiUsageRepository, allocationRepository, connectorTokenRepository, azureConnectionRepository,
                githubConnectionRepository, azureBindingRepository, syncLogRepository);
        return create(GetOrganizationDashboardV2UseCase.class, data, clock());
    }

    private GetProjectOverviewV2UseCase projectUseCase() {
        return create(GetProjectOverviewV2UseCase.class, projectRepository, workItemRepository, sprintRepository,
                new ProjectVcsActivity(read, emptyVcs.commitRepository), read, new EffortBearingLevelResolver(),
                calculator(), aiUsageRepository, allocationRepository, delivery, peopleDirectory(),
                organizationRepository, clock());
    }

    private GetTeamDashboardV2UseCase teamUseCase() {
        return create(GetTeamDashboardV2UseCase.class, projectRepository, peopleDirectory(),
                new DashboardSprintResolver(sprintRepository), new ProjectVcsActivity(read, emptyVcs.commitRepository),
                workItemRepository, new EffortBearingLevelResolver(), calculator(), aiUsageRepository,
                allocationRepository, new ListProjectRepositoriesUseCase(read, delivery), clock());
    }

    private GetProjectRoadmapV2UseCase roadmapUseCase() {
        return create(GetProjectRoadmapV2UseCase.class, projectRepository, workItemRepository, sprintRepository,
                new EffortBearingLevelResolver(), azureBindingRepository, azureConnectionRepository, peopleDirectory(),
                allocationRepository, aiUsageRepository, clock());
    }

    /** The package-private constructors take a Clock; reflection picks the one whose last parameter is a Clock. */
    @SuppressWarnings("unchecked")
    private static <T> T create(Class<T> type, Object... args) {
        try {
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                Class<?>[] parameters = c.getParameterTypes();
                if (parameters.length == args.length && parameters[parameters.length - 1] == Clock.class) {
                    c.setAccessible(true);
                    return (T) c.newInstance(args);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("no constructor with a Clock in " + type);
    }

    private void buildCases() {
        Set<String> all = Set.of(P1, P2, P3);
        for (int days : new int[]{30, 90, 180, 365}) {
            cases.put("org-all-" + days, () -> organizationUseCase().execute(ORG, all, days));
            cases.put("org-p2-" + days, () -> organizationUseCase().execute(ORG, Set.of(P2), days));
        }
        cases.put("project-p1-30", () -> projectUseCase().execute(ORG, P1, 30));
        cases.put("project-p1-365", () -> projectUseCase().execute(ORG, P1, 365));
        cases.put("project-p2-30", () -> projectUseCase().execute(ORG, P2, 30));
        cases.put("project-p2-365", () -> projectUseCase().execute(ORG, P2, 365));
        cases.put("team-p1", () -> teamUseCase().execute(ORG, "user-1", P1, "s-1-cur"));
        cases.put("team-p2", () -> teamUseCase().execute(ORG, "user-3", P2, "s-2-cur"));
        cases.put("roadmap-p1-365", () -> roadmapUseCase().execute(ORG, P1, 365));
        cases.put("roadmap-p3-90", () -> roadmapUseCase().execute(ORG, P3, 90));
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    static Stream<String> caseNames() {
        List<String> names = new ArrayList<>();
        for (int days : new int[]{30, 90, 180, 365}) {
            names.add("org-all-" + days);
            names.add("org-p2-" + days);
        }
        names.addAll(List.of("project-p1-30", "project-p1-365", "project-p2-30", "project-p2-365", "team-p1", "team-p2",
                "roadmap-p1-365", "roadmap-p3-90"));
        return names.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("caseNames")
    void should_matchGolden_forEveryDashboardCase(String name) throws IOException {
        Object canonical = output(name);
        Path file = GOLDEN_DIR.resolve(name + ".json");
        if (Boolean.getBoolean(RECORD_PROPERTY)) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(file, print(canonical), StandardCharsets.UTF_8);
            return;
        }
        assertThat(file).as("golden %s missing: record it with -D%s=true", file, RECORD_PROPERTY).exists();
        Object expected = canonicalize(JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)));
        String diff = firstDifference(expected, canonical, "$");
        assertThat(diff).as("first difference against %s", file).isNull();
    }

    @Test
    void should_exerciseTheEdgeCases_andHaveNoTiesInTheSortedLists() {
        // Edge cases present in the stored data.
        Set<String> orgUsageIds = new HashSet<>();
        Set<String> inactiveIds = new HashSet<>();
        Set<String> otherUsageIds = new HashSet<>();
        for (AIUsage u : usages) {
            if (ORG.equals(u.getOrganizationId())) {
                orgUsageIds.add(u.getId());
                if (!u.isActive()) inactiveIds.add(u.getId());
            } else {
                otherUsageIds.add(u.getId());
            }
        }
        Set<String> allocIds = new HashSet<>();
        allocations.forEach(a -> allocIds.add(a.getId()));
        long repeated = 0, withOther = 0, withInactive = 0, withMissing = 0, validatedEmpty = 0, inactiveValidated = 0,
                pending = 0, nullAllocationId = 0, foreignAllocationId = 0;
        Map<String, AiUsageAllocation> byId = new LinkedHashMap<>();
        for (AiUsageAllocation a : allocations) {
            byId.put(a.getId(), a);
            if (!ORG.equals(a.getOrganizationId())) continue;
            List<String> ids = a.getAiUsageIds();
            if (new HashSet<>(ids).size() < ids.size()) repeated++;
            if (ids.stream().anyMatch(otherUsageIds::contains)) withOther++;
            if (ids.stream().anyMatch(inactiveIds::contains)) withInactive++;
            if (ids.stream().anyMatch(i -> !orgUsageIds.contains(i) && !otherUsageIds.contains(i))) withMissing++;
            if (a.getStatus() == AiUsageAllocation.Status.VALIDATED && ids.isEmpty()) validatedEmpty++;
            if (a.getStatus() == AiUsageAllocation.Status.VALIDATED && !a.isActive()) inactiveValidated++;
            if (a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION) pending++;
        }
        Set<String> coveredByValidated = new HashSet<>();
        allocations.stream().filter(a -> ORG.equals(a.getOrganizationId())
                && a.getStatus() == AiUsageAllocation.Status.VALIDATED && a.isActive())
                .forEach(a -> coveredByValidated.addAll(a.getAiUsageIds()));
        for (AIUsage u : usages) {
            if (!ORG.equals(u.getOrganizationId()) || !coveredByValidated.contains(u.getId())) continue;
            if (u.getAllocationId() == null) nullAllocationId++;
            else {
                AiUsageAllocation target = byId.get(u.getAllocationId());
                if (target == null || !target.getAiUsageIds().contains(u.getId())) foreignAllocationId++;
            }
        }
        assertThat(usages.size()).as("records").isBetween(5_000, 40_000);
        assertThat(repeated).as("allocations with a repeated usage id").isPositive();
        assertThat(withOther).as("allocations with an id of the other organization").isPositive();
        assertThat(withInactive).as("allocations with an inactive record id").isPositive();
        assertThat(withMissing).as("allocations with a non-existent id").isPositive();
        assertThat(validatedEmpty).as("validated allocations with no ids").isPositive();
        assertThat(inactiveValidated).as("validated but inactive allocations").isPositive();
        assertThat(pending).as("pending allocations").isPositive();
        assertThat(nullAllocationId).as("covered records with null allocationId").isPositive();
        assertThat(foreignAllocationId).as("covered records pointing to another allocation").isPositive();

        // Every dashboard has allocated and (where it exists) unallocated cost above zero.
        JsonNode orgAll = tree("org-all-365");
        assertThat(orgAll.get("kpis").get("aiCostUsd").decimalValue()).as("org allocated").isPositive();
        BigDecimal unallocated = null;
        for (JsonNode row : orgAll.get("ai").get("costByProject")) {
            if (row.get("projectId").isNull()) unallocated = row.get("costUsd").decimalValue();
        }
        assertThat(unallocated).as("org unallocated").isNotNull().isPositive();
        assertThat(tree("project-p1-365").get("kpis").get("aiCostUsd").decimalValue()).as("project allocated").isPositive();
        JsonNode team = tree("team-p1");
        assertThat(team.get("kpis").get("aiCostUsd").decimalValue()).as("team allocated").isPositive();
        assertThat(team.get("kpis").get("unallocatedAiCostUsd").decimalValue()).as("team unallocated").isPositive();
        assertThat(maxNumber(tree("roadmap-p1-365"), "aiCostUsd")).as("roadmap allocated").isPositive();

        // No ties between neighbours of the lists sorted by value.
        List<String> ties = new ArrayList<>();
        for (String name : caseNames().toList()) {
            findTies(tree(name), name + ":$", ties);
        }
        assertThat(ties).as("ties in lists sorted by value (adjust the generator)").isEmpty();
    }

    // ------------------------------------------------------------------
    // Canonical JSON
    // ------------------------------------------------------------------

    private JsonNode tree(String name) {
        return JSON.valueToTree(rawOutput(name));
    }

    private Object rawOutput(String name) {
        Supplier<Object> supplier = cases.get(name);
        assertThat(supplier).as("case %s", name).isNotNull();
        return outputs.computeIfAbsent(name, k -> supplier.get());
    }

    private Object output(String name) {
        return canonicalize(tree(name));
    }

    /** Objects as sorted maps, numbers as plain stripped decimals, the rest as is. */
    private static Object canonicalize(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            Map<String, Object> map = new TreeMap<>();
            node.properties().forEach(e -> map.put(e.getKey(), canonicalize(e.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(e -> list.add(canonicalize(e)));
            return list;
        }
        if (node.isNumber()) {
            BigDecimal d = node.decimalValue();
            return d.signum() == 0 ? BigDecimal.ZERO : d.stripTrailingZeros();
        }
        if (node.isBoolean()) return node.booleanValue();
        return node.asString();
    }

    private static String print(Object value) {
        StringBuilder sb = new StringBuilder();
        print(value, 0, sb);
        return sb.append('\n').toString();
    }

    @SuppressWarnings("unchecked")
    private static void print(Object value, int depth, StringBuilder sb) {
        String pad = "  ".repeat(depth + 1);
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
                sb.append(pad).append(JSON.writeValueAsString(e.getKey())).append(": ");
                print(e.getValue(), depth + 1, sb);
                sb.append(++i < map.size() ? ",\n" : "\n");
            }
            sb.append("  ".repeat(depth)).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(pad);
                print(list.get(i), depth + 1, sb);
                sb.append(i < list.size() - 1 ? ",\n" : "\n");
            }
            sb.append("  ".repeat(depth)).append(']');
        } else if (value instanceof BigDecimal d) {
            sb.append(d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString());
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else {
            sb.append(JSON.writeValueAsString(value.toString()));
        }
    }

    /** The first JSON path where the two canonical trees differ (numbers by value), or null. */
    private static String firstDifference(Object expected, Object actual, String path) {
        if (expected == null || actual == null) {
            return expected == actual ? null : path + ": expected " + expected + " but was " + actual;
        }
        if (expected instanceof Map<?, ?> e && actual instanceof Map<?, ?> a) {
            Set<Object> keys = new java.util.TreeSet<>(e.keySet());
            keys.addAll(a.keySet());
            for (Object key : keys) {
                if (!e.containsKey(key)) return path + "." + key + ": unexpected key (value " + a.get(key) + ")";
                if (!a.containsKey(key)) return path + "." + key + ": missing key";
                String d = firstDifference(e.get(key), a.get(key), path + "." + key);
                if (d != null) return d;
            }
            return null;
        }
        if (expected instanceof List<?> e && actual instanceof List<?> a) {
            for (int i = 0; i < Math.min(e.size(), a.size()); i++) {
                String d = firstDifference(e.get(i), a.get(i), path + "[" + i + "]");
                if (d != null) return d;
            }
            return e.size() == a.size() ? null : path + ": expected " + e.size() + " elements but was " + a.size();
        }
        if (expected instanceof BigDecimal e && actual instanceof BigDecimal a) {
            return e.compareTo(a) == 0 ? null : path + ": expected " + e.toPlainString() + " but was " + a.toPlainString();
        }
        return expected.equals(actual) ? null : path + ": expected " + expected + " but was " + actual;
    }

    private static BigDecimal maxNumber(JsonNode node, String key) {
        BigDecimal max = BigDecimal.ZERO;
        if (node.isObject()) {
            for (var e : node.properties()) {
                if (e.getKey().equals(key) && e.getValue().isNumber() && e.getValue().decimalValue().compareTo(max) > 0) {
                    max = e.getValue().decimalValue();
                }
                BigDecimal inner = maxNumber(e.getValue(), key);
                if (inner.compareTo(max) > 0) max = inner;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                BigDecimal inner = maxNumber(child, key);
                if (inner.compareTo(max) > 0) max = inner;
            }
        }
        return max;
    }

    /** Lists {@code byModel} (cost) and {@code byEditor} (interactions): neighbours must differ in the sort value. */
    private static void findTies(JsonNode node, String path, List<String> ties) {
        if (node.isObject()) {
            for (var e : node.properties()) {
                String child = path + "." + e.getKey();
                if (e.getValue().isArray() && (e.getKey().equals("byModel") || e.getKey().equals("byEditor"))) {
                    String field = e.getKey().equals("byModel") ? "costUsd" : "interactions";
                    for (int i = 1; i < e.getValue().size(); i++) {
                        JsonNode prev = e.getValue().get(i - 1).get(field);
                        JsonNode next = e.getValue().get(i).get(field);
                        if (prev != null && next != null && !prev.isNull() && !next.isNull()
                                && prev.decimalValue().compareTo(next.decimalValue()) == 0) {
                            ties.add(child + "[" + (i - 1) + "," + i + "]." + field + "=" + prev.decimalValue());
                        }
                    }
                }
                findTies(e.getValue(), child, ties);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) findTies(node.get(i), path + "[" + i + "]", ties);
        }
    }
}
