package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMethod;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.metrics.domain.PerformanceClass;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStrategicDashboardUseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final LocalDate FROM = LocalDate.parse("2026-08-01");
    private static final LocalDate TO = LocalDate.parse("2026-08-28");
    private static final Instant IN_WINDOW = Instant.parse("2026-08-10T12:00:00Z");
    /** Projects where the caller holds DASHBOARD_STRATEGIC (project-scoped-permissions 7.2). */
    private static final Set<String> VISIBLE = Set.of(PROJECT, "p-a", "p-b");

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private OrganizationRepository organizationRepository;

    private GetStrategicDashboardUseCase useCase;

    @BeforeEach
    void setUp() {
        DeliveryMetricsService deliveryMetricsService = new DeliveryMetricsService(
                vcsReadPort, workItemRepository, organizationRepository);
        useCase = new GetStrategicDashboardUseCase(projectRepository, workItemRepository,
                new EffortBearingLevelResolver(), userRepository, getDeveloperDashboardUseCase, deliveryMetricsService);
        lenient().when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());
        lenient().when(vcsReadPort.repositoriesOfProject(any(), any())).thenReturn(List.of());
        lenient().when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of());
    }

    // ── delivery metrics ──────────────────────────────────────────────────

    @Test
    void should_useVcsProxyInPercent_when_projectHasMergedPullRequests() {
        givenProjects(project(PROJECT));
        givenRepo(PROJECT, repo("repo-1"));
        when(vcsReadPort.mergedPullRequests(
                eq(ORG), eq(List.of("repo-1")), any(), any()))
                .thenReturn(List.of(merged("pr-1", "feature/a"), merged("pr-2", "hotfix/b"),
                        merged("pr-3", "feature/c"), merged("pr-4", "feature/d")));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        DeliveryMetrics delivery = output.projects().get(0).delivery();
        assertThat(delivery.method()).isEqualTo(DeliveryMethod.VCS_PROXY);
        assertThat(delivery.changeFailRatePercent()).isEqualTo(25.0);
        assertThat(delivery.changeFailRateClass()).isEqualTo(PerformanceClass.LOW);
        assertThat(delivery.deploymentsPerWeek()).isEqualTo(1.0);
        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.changeFailureRatePercent()).isEqualTo(25.0);
        assertThat(dora.changeFailureRateClassification()).isEqualTo("LOW");
        assertThat(dora.estimated()).isFalse();
        assertThat(dora.mttrHours()).isNull();
    }

    @Test
    void should_fallBackToBoardsProxy_when_projectHasNoRepository() {
        givenProjects(project(PROJECT));
        WorkItem bug = done("bug", WorkItem.ResolvedType.BUG, null);
        WorkItem story = done("story", WorkItem.ResolvedType.STORY, 3.0);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(bug, story));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        DeliveryMetrics delivery = output.projects().get(0).delivery();
        assertThat(delivery.method()).isEqualTo(DeliveryMethod.BOARDS_PROXY);
        assertThat(delivery.changeFailRatePercent()).isEqualTo(50.0);
        assertThat(output.projects().get(0).dora().estimated()).isTrue();
    }

    @Test
    void should_returnUnavailableWithNullClasses_when_noDataInWindow() {
        givenProjects(project(PROJECT));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        assertThat(output.delivery().method()).isEqualTo(DeliveryMethod.UNAVAILABLE);
        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.changeFailureRatePercent()).isNull();
        assertThat(dora.changeFailureRateClassification()).isNull();
        assertThat(dora.leadTimeClassification()).isNull();
    }

    @Test
    void should_computeOrganizationOverUnionOfProjects_when_repoIsBoundToTwoProjects() {
        givenProjects(project("p-a"), project("p-b"));
        VcsRepository shared = repo("repo-1");
        givenRepo("p-a", shared);
        givenRepo("p-b", shared);
        List<VcsPullRequest> prs = new ArrayList<>();
        prs.add(merged("pr-1", "hotfix/x"));
        for (int i = 2; i <= 4; i++) prs.add(merged("pr-" + i, "feature/" + i));
        when(vcsReadPort.mergedPullRequests(
                eq(ORG), eq(List.of("repo-1")), any(), any())).thenReturn(prs);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(eq(ORG), any())).thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        assertThat(output.level()).isEqualTo("ORG");
        assertThat(output.delivery().mergedPrs()).isEqualTo(4);
        assertThat(output.delivery().remediationPrs()).isEqualTo(1);
        assertThat(output.delivery().changeFailRatePercent()).isEqualTo(25.0);
    }

    @Test
    void should_aggregateOnlyVisibleProjects_when_strategicGrantedOnSomeProjects() {
        givenProjects(project("p-a"), project("p-b"));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(eq(ORG), any())).thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, Set.of("p-a"));

        assertThat(output.projects()).extracting(StrategicDashboardOutput.ProjectSummary::projectId)
                .containsExactly("p-a");
        verify(workItemRepository, times(0)).findAllByOrganizationIdAndProjectId(ORG, "p-b");
    }

    // ── velocity (single definition) and percentiles ─────────────────────

    @Test
    void should_useEffectiveEffortAndIgnoreStoryPoints_when_tasksCarryEffort() {
        givenProjects(project(PROJECT));
        WorkItem story = done("s", WorkItem.ResolvedType.STORY, null);
        story.setStoryPoints(13.0);
        WorkItem t1 = done("t1", WorkItem.ResolvedType.TASK, 3.0);
        t1.setParentExternalId(story.getExternalId());
        WorkItem t2 = done("t2", WorkItem.ResolvedType.TASK, 5.0);
        t2.setParentExternalId(story.getExternalId());
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(story, t1, t2));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        assertThat(output.projects().get(0).storyPointsDelivered()).isEqualTo(8.0);
    }

    @Test
    void should_includeFeatureTypedWorkItem_inProjectSummaryVelocity_notDropIt() {
        givenProjects(project(PROJECT));
        WorkItem feature = done("feat", WorkItem.ResolvedType.FEATURE, 8.0);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(feature));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        assertThat(output.projects().get(0).storyPointsDelivered()).isEqualTo(8.0);
    }

    @Test
    void should_ignoreItemsCompletedOutsideWindow_when_summingVelocity() {
        givenProjects(project(PROJECT));
        WorkItem inside = done("in", WorkItem.ResolvedType.STORY, 5.0);
        WorkItem outside = done("out", WorkItem.ResolvedType.STORY, 8.0);
        outside.setCompletedAt(TO.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(inside, outside));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", null, null, FROM, TO, VISIBLE);

        assertThat(output.projects().get(0).storyPointsDelivered()).isEqualTo(5.0);
        assertThat(output.projects().get(0).totalWorkItems()).isEqualTo(1);
    }

    @Test
    void should_reportMemberPercentilesAndLoadUsersOnce_when_projectLevel() {
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project(PROJECT)));
        List<WorkItem> items = new ArrayList<>();
        double[] cycles = {2, 3, 3, 4, 100};
        for (int i = 0; i < cycles.length; i++) {
            WorkItem item = done("wi-" + i, WorkItem.ResolvedType.STORY, 1.0);
            item.setAssigneeUserId("user-1");
            item.setCycleTimeHours(cycles[i]);
            items.add(item);
        }
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(items);
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(new User("user-1", ORG, "d@x", "h",
                null, null, null, null, "azure-1", null, null, true, true, 1L, IN_WINDOW, IN_WINDOW)));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth", PROJECT, null, FROM, TO, VISIBLE);

        StrategicDashboardOutput.MemberSummary member = output.projectDetail().members().get(0);
        assertThat(member.name()).isEqualTo("d@x");
        assertThat(member.linkedToAzure()).isTrue();
        assertThat(member.storyPointsDelivered()).isEqualTo(5.0);
        assertThat(member.cycleTime().p50()).isEqualTo(3.0);
        assertThat(member.cycleTime().p90()).isEqualTo(100.0);
        assertThat(output.projectDetail().delivery().method()).isEqualTo(DeliveryMethod.BOARDS_PROXY);
        assertThat(output.delivery()).isEqualTo(output.projectDetail().delivery());
        verify(userRepository, times(1)).findAllByOrganizationId(ORG);
    }

    @Test
    void should_delegateToDeveloperDashboard_when_memberRequested() {
        StrategicDashboardOutput output = useCase.execute(ORG, "auth", PROJECT, "user-1", FROM, TO, VISIBLE);

        assertThat(output.level()).isEqualTo("MEMBER");
        verify(getDeveloperDashboardUseCase).execute(ORG, "user-1", PROJECT, null, FROM, TO, null);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void givenProjects(Project... projects) {
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(projects));
    }

    private void givenRepo(String projectId, VcsRepository repo) {
        when(vcsReadPort.repositoriesOfProject(ORG, projectId)).thenReturn(List.of(repo));
    }

    private Project project(String id) {
        return new Project(id, ORG, "Project " + id, id, "desc", true, List.of(), 1L, IN_WINDOW, IN_WINDOW);
    }

    private VcsRepository repo(String id) {
        VcsRepository repo = VcsLegacyFixtures.repository(id, ORG, "ext-" + id, "octo", "hello", "octo/hello",
                IN_WINDOW, true, IN_WINDOW, IN_WINDOW, 1L);
        repo.setDefaultBranch("main");
        return repo;
    }

    private VcsPullRequest merged(String id, String sourceBranch) {
        VcsPullRequest pr = VcsLegacyFixtures.pullRequest(id, ORG, "repo-1", "ext-" + id, 1, "PR " + id,
                PullRequestStatus.MERGED, "octocat", null, IN_WINDOW.minus(Duration.ofHours(10)),
                IN_WINDOW, IN_WINDOW, IN_WINDOW, 10.0, null, true, IN_WINDOW, 1L);
        pr.setBaseBranch("main");
        pr.setSourceBranch(sourceBranch);
        return pr;
    }

    private WorkItem done(String id, WorkItem.ResolvedType type, Double effort) {
        WorkItem item = WorkItemFixture.item(id, type, WorkItem.ResolvedStatus.DONE, effort, null);
        item.setCompletedAt(IN_WINDOW);
        return item;
    }
}
