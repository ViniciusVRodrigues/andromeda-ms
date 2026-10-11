package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.Assignee;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.CommitKindTotal;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedType;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.9 — project quality v2, table tests ported from the preview's
 * {@code buildQuality}: open bugs, created × closed, time to fix (p50/p90), reopen rate from the
 * transitions, commit types and remediation pull requests. Now = 2026-10-11 12:00Z; 90 days =
 * 2026-07-14 → 2026-10-11, weekly buckets from 20/07.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetProjectQualityV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final String REPO = "repo-1";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkItemTransitionRepository transitionRepository;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private VcsCommitRepository commitRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;

    private final List<WorkItem> items = new ArrayList<>();
    private GetProjectQualityV2UseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProjectQualityV2UseCase(projectRepository, workItemRepository, transitionRepository,
                new ProjectVcsActivity(vcsReadPort, commitRepository),
                new DeliveryMetricsService(vcsReadPort, workItemRepository, organizationRepository),
                new DashboardPeopleDirectory(userRepository, connectorTokenRepository), Clock.fixed(NOW, ZoneOffset.UTC));
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of("user-a"), 0L, NOW, NOW)));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(items);
    }

    @Test
    void should_throwProjectNotFound_when_projectIsNotOfTheOrganization() {
        assertThatThrownBy(() -> useCase.execute("org-2", PROJECT, 90)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_measureTimeToFix_asTheLeadTimeP50P90_ofTheBugsClosedInThePeriod() {
        items.addAll(List.of(closedBug("b1", 10.0, "2026-07-21T10:00:00Z"), closedBug("b2", 20.0, "2026-07-22T10:00:00Z"),
                closedBug("b3", 30.0, "2026-08-04T10:00:00Z"), closedBug("b4", 100.0, "2026-09-01T10:00:00Z"),
                closedBug("old", 999.0, "2026-07-01T10:00:00Z"),
                done("s1", ResolvedType.STORY, "2026-07-21T10:00:00Z", 5.0)));

        ProjectQualityV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.kpis().resolutionHours()).isEqualTo(new DurationStat(20.0, 100.0, 4));
        assertThat(out.kpis().bugsClosed()).isEqualTo(4);
        assertThat(out.bugFlow().getFirst().closed()).isEqualTo(2);
        assertThat(out.bugFlow().get(2).closed()).isEqualTo(1);
    }

    @Test
    void should_countOpenBugsAnywhere_andBugsCreatedInThePeriod_listingTheOldestFirst() {
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(new User("user-a", ORG, "a@x.io", null,
                "Ana", "role", null, null, null, null, null, true, true, 0L, NOW, NOW)));
        WorkItem young = openBug("o1", "2026-09-30T10:00:00Z");
        young.setAssigneeUserId("user-a");
        young.setPriority("2");
        WorkItem old = openBug("o2", "2026-01-11T12:00:00Z");
        old.setSprintId("s9");
        items.addAll(List.of(young, old, openBug("o3", "2026-07-20T10:00:00Z"),
                closedBug("c1", 5.0, "2026-08-01T10:00:00Z"),
                WorkItemFixture.item("st", ResolvedType.STORY, ResolvedStatus.TODO)));

        ProjectQualityV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.kpis().openBugs()).isEqualTo(3);
        // o1, o3 and the closed c1 (created 2026-08-01) — o2 was created before the period
        assertThat(out.kpis().bugsCreated()).isEqualTo(3);
        assertThat(out.bugFlow().getFirst().created()).isEqualTo(1);
        assertThat(out.oldestOpen()).extracting(ProjectQualityV2Output.OpenBug::externalId)
                .containsExactly("ext-o2", "ext-o3", "ext-o1");
        assertThat(out.oldestOpen().getFirst().ageDays()).isEqualTo(273.0);
        assertThat(out.oldestOpen().getFirst().inSprint()).isTrue();
        assertThat(out.oldestOpen().getLast().assignee()).isEqualTo(new Assignee("user-a", "Ana"));
        assertThat(out.oldestOpen().getLast().priority()).isEqualTo(2);
    }

    @Test
    void should_computeTheReopenRate_fromTransitionsOutOfDone_scopedByOrganization() {
        WorkItem reopened = done("d1", ResolvedType.STORY, "2026-07-21T10:00:00Z", 5.0);
        WorkItem clean = done("d2", ResolvedType.BUG, "2026-07-22T10:00:00Z", 5.0);
        items.addAll(List.of(reopened, clean, done("old", ResolvedType.STORY, "2026-06-01T10:00:00Z", 1.0)));
        when(transitionRepository.findActiveByOrganizationIdAndWorkItemIdIn(eq(ORG), anyCollection())).thenReturn(List.of(
                transition("d1", ResolvedStatus.IN_PROGRESS, 1), transition("d1", ResolvedStatus.DONE, 2),
                transition("d1", ResolvedStatus.IN_PROGRESS, 3), transition("d1", ResolvedStatus.DONE, 4),
                transition("d2", ResolvedStatus.IN_PROGRESS, 1), transition("d2", ResolvedStatus.DONE, 2)));

        ProjectQualityV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.kpis().reopenRatePct()).isEqualTo(50.0);
        assertThat(out.kpis().reopenedItems()).isEqualTo(1);
        assertThat(out.rework().getFirst().reopened()).isEqualTo(1);
        assertThat(out.rework().getFirst().ratePct()).isEqualTo(50.0);
        assertThat(out.rework().get(1).ratePct()).isNull();
        verify(transitionRepository).findActiveByOrganizationIdAndWorkItemIdIn(ORG, Set.of("d1", "d2"));
    }

    @Test
    void should_classifyCommitsByMessage_andCountRemediationPullRequests() {
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(
                new VcsRepository(REPO, ORG, VcsProvider.GITHUB, "ext-repo", "owner/orion")));
        when(commitRepository.findMessagesByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(eq(ORG), any(),
                eq(Instant.parse("2026-07-14T00:00:00Z")), eq(Instant.parse("2026-10-12T00:00:00Z"))))
                .thenReturn(List.of(commit("c1", "fix: login", "2026-07-21T10:00:00Z"),
                        commit("c2", "feat(api): rota", "2026-07-22T10:00:00Z"),
                        commit("c3", "fix(ui)!: botão", "2026-08-04T10:00:00Z"),
                        commit("c4", "Merge branch main", "2026-08-05T10:00:00Z")));
        when(vcsReadPort.pullRequestsTouchedSince(eq(ORG), eq(List.of(REPO)), eq(Instant.parse("2026-07-14T00:00:00Z"))))
                .thenReturn(List.of(
                merged("pr1", "Revert \"feat: rota\"", "2026-08-01T10:00:00Z"),
                merged("pr2", "feat: rota", "2026-08-02T10:00:00Z"),
                merged("pr3", "Revert old", "2026-05-02T10:00:00Z")));

        ProjectQualityV2Output out = useCase.execute(ORG, PROJECT, 90);

        assertThat(out.kpis().fixCommitsPct()).isEqualTo(50.0);
        assertThat(out.totals().commits()).isEqualTo(4);
        assertThat(out.commitKinds().getFirst().fix()).isEqualTo(1);
        assertThat(out.commitKinds().getFirst().feat()).isEqualTo(1);
        assertThat(out.commitKinds().get(2).other()).isEqualTo(1);
        assertThat(out.commitKindTotals()).contains(new CommitKindTotal("fix", 2), new CommitKindTotal("other", 1));
        assertThat(out.kpis().mergedPrs()).isEqualTo(2);
        assertThat(out.kpis().remediationPrs()).isEqualTo(1);
        assertThat(out.kpis().remediationPrsPct()).isEqualTo(50.0);
    }

    @Test
    void should_returnNullsAndEmptyLists_butEveryBucket_when_thePeriodHasNoData() {
        ProjectQualityV2Output out = useCase.execute(ORG, PROJECT, 30);

        assertThat(out.period().granularity()).isEqualTo("semana");
        assertThat(out.kpis().openBugs()).isZero();
        assertThat(out.kpis().resolutionHours()).isEqualTo(DurationStat.empty());
        assertThat(out.kpis().reopenRatePct()).isNull();
        assertThat(out.kpis().fixCommitsPct()).isNull();
        assertThat(out.kpis().remediationPrsPct()).isNull();
        assertThat(out.bugFlow()).hasSize(4).allSatisfy(r -> assertThat(r.created() + r.closed()).isZero());
        assertThat(out.rework()).hasSize(4).allSatisfy(r -> assertThat(r.ratePct()).isNull());
        assertThat(out.commitKinds()).hasSize(4);
        assertThat(out.commitKindTotals()).hasSize(7).allSatisfy(t -> assertThat(t.count()).isZero());
        assertThat(out.oldestOpen()).isEmpty();
        verify(transitionRepository, never()).findActiveByOrganizationIdAndWorkItemIdIn(any(), anyCollection());
    }

    private static WorkItem done(String id, ResolvedType type, String completedAt, Double leadHours) {
        WorkItem item = WorkItemFixture.item(id, type, ResolvedStatus.DONE);
        item.setCompletedAt(Instant.parse(completedAt));
        item.setLeadTimeHours(leadHours);
        item.setCreatedAt(Instant.parse(completedAt).minusSeconds((long) (leadHours * 3600)));
        return item;
    }

    private static WorkItem closedBug(String id, Double leadHours, String completedAt) {
        return done(id, ResolvedType.BUG, completedAt, leadHours);
    }

    private static WorkItem openBug(String id, String createdAt) {
        WorkItem item = WorkItemFixture.item(id, ResolvedType.BUG, ResolvedStatus.IN_PROGRESS);
        item.setCreatedAt(Instant.parse(createdAt));
        return item;
    }

    private static WorkItemTransition transition(String workItemId, ResolvedStatus to, int hour) {
        Instant at = Instant.parse("2026-07-20T00:00:00Z").plusSeconds(hour * 3600L);
        return new WorkItemTransition(workItemId + "-" + hour, ORG, workItemId, "x", to.name(), to, at, at,
                true, 0L, at, at);
    }

    private static VcsCommit commit(String id, String message, String authoredAt) {
        VcsCommit commit = new VcsCommit(id, ORG, REPO, VcsProvider.GITHUB, id);
        commit.setMessage(message);
        commit.setAuthoredAt(Instant.parse(authoredAt));
        return commit;
    }

    private static VcsPullRequest merged(String id, String title, String mergedAt) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, REPO, VcsProvider.GITHUB, "ext-" + id,
                Integer.parseInt(id.replaceAll("\\D", "")), title, PullRequestStatus.MERGED);
        pr.setCreatedAt(Instant.parse(mergedAt).minusSeconds(3600));
        pr.setMergedAt(Instant.parse(mergedAt));
        return pr;
    }
}
