package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.14, QA finding B1 — the organization overview is linear in its data:
 * 20 projects × 2 000 items, 20 000 validated allocations (2 splits each), 10 000 pull requests and
 * 40 000 usage records over 365 days are computed in memory well under a generous bound. The
 * quadratic version (each allocation × each project rebuilding the project's id set) needed
 * 20 000 × 20 × 2 000 = 800 million set insertions for the allocated cost alone.
 */
class GetOrganizationDashboardV2ScaleTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final int PROJECTS = 20;
    private static final int ITEMS_PER_PROJECT = 2_000;
    private static final int ALLOCATIONS = 20_000;
    private static final int PULL_REQUESTS = 10_000;
    private static final int USERS = 30;
    private static final Duration BOUND = Duration.ofSeconds(5);
    private static final WorkItem.ResolvedType[] TYPES = {WorkItem.ResolvedType.STORY, WorkItem.ResolvedType.BUG,
            WorkItem.ResolvedType.TASK};

    @Test
    void should_computeABigOrganization_inLinearTime() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WorkItemRepository workItemRepository = mock(WorkItemRepository.class);
        AIUsageRepository aiUsageRepository = mock(AIUsageRepository.class);
        AiUsageAllocationRepository allocationRepository = mock(AiUsageAllocationRepository.class);
        VcsRepoBindingRepository bindingRepository = mock(VcsRepoBindingRepository.class);
        VcsRepositoryRepository repositoryRepository = mock(VcsRepositoryRepository.class);
        VcsReadPort vcsReadPort = mock(VcsReadPort.class);
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);

        List<String> userIds = new ArrayList<>();
        List<User> users = new ArrayList<>();
        for (int u = 0; u < USERS; u++) {
            userIds.add("u" + u);
            users.add(new User("u" + u, ORG, "u" + u + "@x.io", null, "U" + u, "r", null, null, null, null, null,
                    true, true, 0L, NOW, NOW));
        }
        List<Project> projects = new ArrayList<>();
        List<VcsRepoBinding> bindings = new ArrayList<>();
        List<VcsRepository> repositories = new ArrayList<>();
        Map<String, List<WorkItem>> items = new HashMap<>();
        List<String> allItemIds = new ArrayList<>();
        for (int p = 0; p < PROJECTS; p++) {
            String projectId = "p" + p;
            projects.add(new Project(projectId, ORG, "Project " + p, projectId, null, true, userIds.subList(0, 5 + p),
                    0L, NOW, NOW));
            bindings.add(new VcsRepoBinding("b" + p, ORG, projectId, "r" + p, VcsProvider.GITHUB, true));
            repositories.add(new VcsRepository("r" + p, ORG, VcsProvider.GITHUB, "ext" + p, "org/repo" + p));
            List<WorkItem> list = new ArrayList<>();
            for (int i = 0; i < ITEMS_PER_PROJECT; i++) {
                boolean done = i % 2 == 0;
                WorkItem item = WorkItemFixture.item(projectId + "-" + i, TYPES[i % 3],
                        done ? WorkItem.ResolvedStatus.DONE : WorkItem.ResolvedStatus.IN_PROGRESS, (double) (1 + i % 5), null);
                item.setProjectId(projectId);
                if (done) item.setCompletedAt(NOW.minus(Duration.ofDays(i % 700)));
                item.setLeadTimeHours(10.0 + i % 50);
                item.setCycleTimeHours(5.0 + i % 30);
                list.add(item);
                allItemIds.add(item.getId());
            }
            items.put(projectId, list);
            when(workItemRepository.findMetricFieldsByOrganizationIdAndProjectId(ORG, projectId)).thenReturn(list);
            when(workItemRepository.findActiveIdsByOrganizationIdAndProjectId(ORG, projectId))
                    .thenReturn(new HashSet<>(list.stream().map(WorkItem::getId).toList()));
        }
        List<AIUsage> usage = new ArrayList<>();
        List<AiUsageAllocation> allocations = new ArrayList<>();
        for (int a = 0; a < ALLOCATIONS * 2; a++) {
            LocalDate day = LocalDate.of(2026, 10, 11).minusDays(a % 700);
            String usageId = "au" + a;
            boolean allocated = a < ALLOCATIONS;
            usage.add(new AIUsage(usageId, ORG, userIds.get(a % USERS), null, null, day, "model" + (a % 3), null, null,
                    null, null, 100, 20, 1, "ed" + (a % 2), allocated ? "al" + a : null, BigDecimal.ONE, NOW, true, null,
                    null, null));
            if (allocated) {
                allocations.add(new AiUsageAllocation("al" + a, ORG, userIds.get(a % USERS), day, List.of(usageId),
                        List.of(new AllocationSplit(allItemIds.get(a % allItemIds.size()), "x", new BigDecimal("0.5")),
                                new AllocationSplit(allItemIds.get((a * 7) % allItemIds.size()), "y", new BigDecimal("0.5"))),
                        AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW));
            }
        }
        List<VcsPullRequest> prs = new ArrayList<>();
        for (int i = 0; i < PULL_REQUESTS; i++) {
            boolean merged = i % 4 != 0;
            VcsPullRequest pr = new VcsPullRequest("pr" + i, ORG, "r" + (i % PROJECTS), VcsProvider.GITHUB, "e" + i, i,
                    "feat " + i, merged ? PullRequestStatus.MERGED : PullRequestStatus.OPEN);
            pr.setCreatedAt(NOW.minus(Duration.ofDays(i % 700 + 1)));
            pr.setMergedAt(merged ? NOW.minus(Duration.ofDays(i % 700)) : null);
            pr.setCycleTimeHours(10.0);
            pr.setTimeToFirstReviewHours(2.0);
            pr.setBaseBranch("main");
            pr.setActive(true);
            prs.add(pr);
        }
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(projects);
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(users);
        when(aiUsageRepository.findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(eq(ORG), anyCollection(),
                any(), any())).thenAnswer(inv -> {
                    java.util.Collection<String> asked = inv.getArgument(1);
                    LocalDate from = inv.getArgument(2);
                    LocalDate to = inv.getArgument(3);
                    return usage.stream().filter(u -> asked.contains(u.getUserId()) && !u.getDay().isBefore(from)
                            && !u.getDay().isAfter(to)).toList();
                });
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(eq(ORG), anyCollection(),
                any(), any())).thenReturn(allocations);
        when(bindingRepository.findAllActiveByOrganizationId(ORG)).thenReturn(bindings);
        when(repositoryRepository.findAllByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(repositories);
        when(vcsReadPort.pullRequestMetricFieldsTouchedSince(eq(ORG), anyCollection(), any())).thenAnswer(inv -> {
            java.util.Collection<String> repositoryIds = inv.getArgument(1);
            return prs.stream().filter(pr -> repositoryIds.contains(pr.getRepositoryId())).toList();
        });
        when(organizationRepository.findById(anyString())).thenReturn(java.util.Optional.empty());

        OrganizationDashboardData data = new OrganizationDashboardData(projectRepository, userRepository,
                workItemRepository, mock(SprintRepository.class), new EffortBearingLevelResolver(), bindingRepository,
                repositoryRepository, vcsReadPort, mock(VcsCommitRepository.class),
                new DeliveryMetricsService(vcsReadPort, workItemRepository, organizationRepository),
                organizationRepository, aiUsageRepository, allocationRepository, mock(ConnectorTokenRepository.class),
                mock(AzureConnectionRepository.class),
                mock(GithubConnectionRepository.class), mock(AzureProjectBindingRepository.class),
                mock(IntegrationSyncLogRepository.class));
        GetOrganizationDashboardV2UseCase useCase = new GetOrganizationDashboardV2UseCase(data,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Set<String> visible = new HashSet<>(projects.stream().map(Project::getId).toList());
        useCase.execute(ORG, visible, 30); // warm-up (JIT, class loading)

        long start = System.nanoTime();
        OrganizationDashboardV2Output out = useCase.execute(ORG, visible, 365);
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        System.out.printf("organization v2, %d projects × %d items, %d allocations, %d PRs, 365 days: %d ms%n",
                PROJECTS, ITEMS_PER_PROJECT, ALLOCATIONS, PULL_REQUESTS, took.toMillis());
        assertThat(took).isLessThan(BOUND);
        assertThat(out.portfolio()).hasSize(PROJECTS);
        // every allocation of the window splits in full to visible items: Σ allocated = allocated usage cost
        BigDecimal allocated = out.ai().costByProject().stream().filter(r -> r.projectId() != null)
                .map(r -> r.costUsd()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long allocationsInWindow = allocations.stream()
                .filter(a -> !a.getDay().isBefore(LocalDate.of(2026, 10, 11).minusDays(364))).count();
        assertThat(allocated).isEqualByComparingTo(BigDecimal.valueOf(allocationsInWindow));
    }
}
