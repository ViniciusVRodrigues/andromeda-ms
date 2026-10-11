package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.BranchCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.FlowDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.LabelCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StageTimesPoint;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.13 — team flow v2, table tests ported from the preview's
 * {@code buildTeamFlow}: cumulative flow per day with the three recorded categories, time per stage
 * as p50/p90/n, reopened items, open pull requests with draft/base/labels and the "not done yet"
 * KPI. Now = Thursday 2026-10-08 12:00Z; the current sprint runs Thu 2026-10-01 → 2026-10-14.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetTeamFlowV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final String ME = "user-a";
    private static final String REPO = "repo-1";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final Sprint S1 = sprint("s1", "Sprint 1", Sprint.TimeFrame.PAST, "2026-09-17", "2026-09-30");
    private static final Sprint S2 = sprint("s2", "Sprint 2", Sprint.TimeFrame.CURRENT, "2026-10-01", "2026-10-14");

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private WorkItemTransitionRepository transitionRepository;

    private final List<WorkItemTransition> transitions = new ArrayList<>();
    private final List<WorkItem> projectItems = new ArrayList<>();
    private final List<VcsPullRequest> pullRequests = new ArrayList<>();
    private GetTeamFlowV2UseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTeamFlowV2UseCase(projectRepository,
                new DashboardPeopleDirectory(userRepository, connectorTokenRepository),
                new DashboardSprintResolver(sprintRepository), vcsReadPort, workItemRepository, transitionRepository,
                new EffortBearingLevelResolver(), Clock.fixed(NOW, ZoneOffset.UTC));

        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of(ME), 0L, NOW, NOW)));
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(user(ME, "Ana Ribeiro")));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(S1, S2));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s1", ORG)).thenReturn(Optional.of(S1));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s2", ORG)).thenReturn(Optional.of(S2));
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(
                new VcsRepository(REPO, ORG, VcsProvider.GITHUB, "ext-repo", "owner/orion")));
        when(workItemRepository.findActiveByOrganizationIdAndProjectIdAndSprintIdIn(eq(ORG), eq(PROJECT), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<String> sprintIds = inv.getArgument(2);
                    return projectItems.stream().filter(i -> sprintIds.contains(i.getSprintId())).toList();
                });
        when(workItemRepository.findActiveByOrganizationIdAndProjectIdAndCompletedAtInRange(eq(ORG), eq(PROJECT),
                any(Instant.class), any(Instant.class))).thenAnswer(inv -> {
                    Instant from = inv.getArgument(2);
                    Instant to = inv.getArgument(3);
                    return projectItems.stream().filter(i -> i.getCompletedAt() != null
                            && !i.getCompletedAt().isBefore(from) && i.getCompletedAt().isBefore(to)).toList();
                });
        when(vcsReadPort.pullRequestsCreatedIn(eq(ORG), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(2);
                    Instant to = inv.getArgument(3);
                    return pullRequests.stream().filter(p -> p.getCreatedAt().isAfter(from)
                            && p.getCreatedAt().isBefore(to)).toList();
                });
        when(vcsReadPort.openPullRequests(eq(ORG), anyCollection())).thenAnswer(inv -> pullRequests.stream()
                .filter(p -> p.getStatus() == PullRequestStatus.OPEN).toList());
        when(transitionRepository.findActiveByOrganizationIdAndWorkItemIdIn(eq(ORG), anyCollection()))
                .thenAnswer(inv -> {
                    Collection<String> ids = inv.getArgument(1);
                    return transitions.stream().filter(t -> ids.contains(t.getWorkItemId())).toList();
                });
    }

    @Test
    void should_throwSprintNotFound_when_sprintIsOfAnotherProject() {
        Sprint foreign = sprint("s-x", "Sprint X", Sprint.TimeFrame.CURRENT, "2026-10-01", "2026-10-14");
        foreign.setProjectId("project-2");
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s-x", ORG)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> useCase.execute(ORG, PROJECT, "s-x")).isInstanceOf(SprintNotFoundException.class);
    }

    @Test
    void should_throwProjectNotFound_when_projectIsOfAnotherOrganization() {
        when(projectRepository.findActiveByIdAndOrganizationId("foreign", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, "foreign", null)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_returnNullsAndEmptyLists_when_projectHasNoSprint() {
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of());

        TeamFlowV2Output out = useCase.execute(ORG, PROJECT, null);

        assertThat(out.sprint()).isNull();
        assertThat(out.cfd()).isEmpty();
        assertThat(out.states()).isEmpty();
        assertThat(out.stateTimes()).isNull();
        assertThat(out.rework()).isNull();
        assertThat(out.scope()).isNull();
        assertThat(out.reviewQueue()).isNull();
        assertThat(out.asOf()).isEqualTo(NOW);
    }

    @Test
    void should_countItemsPerCategory_atTheEndOfEachWorkingDay() {
        WorkItem x = item("x", ResolvedStatus.DONE, 1.0, "2026-09-28T00:00:00Z");
        transition("x", ResolvedStatus.IN_PROGRESS, "2026-10-02T10:00:00Z");
        transition("x", ResolvedStatus.DONE, "2026-10-06T09:00:00Z");
        WorkItem y = item("y", ResolvedStatus.TODO, 1.0, "2026-10-05T08:00:00Z");
        WorkItem z = item("z", ResolvedStatus.IN_PROGRESS, 1.0, "2026-09-20T00:00:00Z");
        z.setStartedAt(Instant.parse("2026-10-01T09:00:00Z"));
        WorkItem epic = item("epic", ResolvedStatus.TODO, null, "2026-09-20T00:00:00Z");
        epic.setResolvedType(WorkItem.ResolvedType.EPIC);
        sprintItems("s2", x, y, z, epic);

        List<FlowDay> cfd = useCase.execute(ORG, PROJECT, "s2").cfd();

        assertThat(cfd).extracting(FlowDay::date).containsExactly(
                "2026-10-01", "2026-10-02", "2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08");
        assertThat(cfd.getFirst().label()).isEqualTo("01/10");
        assertThat(cfd).extracting(d -> List.of(d.proposed(), d.inProgress(), d.completed())).containsExactly(
                List.of(1, 1, 0),   // x before its first transition, y not created yet
                List.of(0, 2, 0),
                List.of(1, 2, 0),   // y appears
                List.of(1, 1, 1),   // x done
                List.of(1, 1, 1),
                List.of(1, 1, 1));
    }

    @Test
    void should_measureTimePerStage_asP50P90N_fromTheStateSegments() {
        WorkItem a = doneItem("a", "2026-09-30T00:00:00Z", "2026-10-01T08:00:00Z", "2026-10-02T12:00:00Z", 28.0);
        transition("a", ResolvedStatus.IN_PROGRESS, "2026-10-01T08:00:00Z");
        transition("a", ResolvedStatus.TODO, "2026-10-01T18:00:00Z");       // 10h in progress
        transition("a", ResolvedStatus.IN_PROGRESS, "2026-10-02T08:00:00Z");
        transition("a", ResolvedStatus.DONE, "2026-10-02T12:00:00Z");       // + 4h = 14h, not the 28h cycle time
        WorkItem b = doneItem("b", "2026-10-01T12:00:00Z", "2026-10-02T00:00:00Z", "2026-10-02T20:00:00Z", 20.0);
        WorkItem c = doneItem("c", "2026-09-20T00:00:00Z", "2026-09-25T00:00:00Z", "2026-10-03T00:00:00Z", 30.0);
        sprintItems("s2", a, b, c, item("open", ResolvedStatus.TODO, 1.0, "2026-10-01T00:00:00Z"));

        TeamFlowV2Output out = useCase.execute(ORG, PROJECT, "s2");

        // queue: a = 8h (sprint start → start), b = 12h (created mid-sprint), c started before the sprint: out
        assertThat(out.stateTimes().current().queueHours()).isEqualTo(new DurationStat(8.0, 12.0, 2));
        // in progress: a = 14h (segments), b = 20h and c = 30h (no transitions → cycle time)
        assertThat(out.stateTimes().current().inProgressHours()).isEqualTo(new DurationStat(20.0, 30.0, 3));
        assertThat(out.stateTimes().current().resolvedHours()).isNull();
        assertThat(out.stateTimes().history()).extracting(StageTimesPoint::sprintId, StageTimesPoint::label)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("s1", "S1"),
                        org.assertj.core.groups.Tuple.tuple("s2", "S2*"));
        assertThat(out.stateTimes().history().get(0).inProgressHours()).isEqualTo(DurationStat.empty());
    }

    @Test
    void should_countReopened_amongTheItemsFirstCompletedInTheSprint() {
        WorkItem r1 = projectItem("r1", ResolvedStatus.IN_PROGRESS, "2026-10-02T10:00:00Z");
        r1.setSprintId("s1");     // project-wide, whatever the sprint
        transition("r1", ResolvedStatus.IN_PROGRESS, "2026-10-01T10:00:00Z");
        transition("r1", ResolvedStatus.DONE, "2026-10-02T10:00:00Z");
        transition("r1", ResolvedStatus.IN_PROGRESS, "2026-10-05T10:00:00Z");
        projectItem("r2", ResolvedStatus.DONE, "2026-10-03T10:00:00Z");
        transition("r2", ResolvedStatus.DONE, "2026-10-03T10:00:00Z");
        projectItem("never", ResolvedStatus.TODO, null);
        transition("never", ResolvedStatus.IN_PROGRESS, "2026-10-02T10:00:00Z");
        transition("never", ResolvedStatus.TODO, "2026-10-03T10:00:00Z");
        projectItem("old", ResolvedStatus.IN_PROGRESS, "2026-09-20T10:00:00Z");
        transition("old", ResolvedStatus.DONE, "2026-09-20T10:00:00Z");
        transition("old", ResolvedStatus.IN_PROGRESS, "2026-10-05T10:00:00Z");
        WorkItem epic = projectItem("epic", ResolvedStatus.DONE, "2026-10-02T10:00:00Z");
        epic.setResolvedType(WorkItem.ResolvedType.EPIC);

        TeamFlowV2Output out = useCase.execute(ORG, PROJECT, "s2");

        assertThat(out.rework().doneInWindow()).isEqualTo(2);
        assertThat(out.rework().reopened()).extracting(ItemView::id).containsExactly("r1");
        assertThat(out.rework().reopened().getFirst().assignee().name()).isEqualTo("Ana Ribeiro");
        assertThat(out.rework().ratePct()).isEqualTo(50.0);
    }

    @Test
    void should_capTheReopenedList_butComputeTheRateFromEveryReopenedItem() {
        int total = GetTeamFlowV2UseCase.REOPENED_LIST_LIMIT + 5;
        for (int i = 0; i < total; i++) {
            String at = Instant.parse("2026-10-02T10:00:00Z").plusSeconds(i).toString();
            projectItem("r" + i, ResolvedStatus.IN_PROGRESS, at);
            transition("r" + i, ResolvedStatus.DONE, at);
            transition("r" + i, ResolvedStatus.IN_PROGRESS, "2026-10-05T10:00:00Z");
        }

        var rework = useCase.execute(ORG, PROJECT, "s2").rework();

        assertThat(rework.reopened()).hasSize(GetTeamFlowV2UseCase.REOPENED_LIST_LIMIT);
        assertThat(rework.doneInWindow()).isEqualTo(total);
        assertThat(rework.ratePct()).isEqualTo(100.0);
    }

    @Test
    void should_notCountAsReopened_when_itemNeverReachedDone() {
        projectItem("never", ResolvedStatus.TODO, null);
        transition("never", ResolvedStatus.IN_PROGRESS, "2026-10-02T10:00:00Z");
        transition("never", ResolvedStatus.TODO, "2026-10-03T10:00:00Z");

        TeamFlowV2Output out = useCase.execute(ORG, PROJECT, "s2");

        assertThat(out.rework().reopened()).isEmpty();
        assertThat(out.rework().doneInWindow()).isZero();
        assertThat(out.rework().ratePct()).isNull();
    }

    @Test
    void should_summarizeOpenPullRequests_withDraftsBaseBranchesAndLabels() {
        VcsPullRequest p1 = pr("p1", PullRequestStatus.OPEN, "2026-10-02T10:00:00Z", true, "main", "bug", "ui");
        VcsPullRequest p2 = pr("p2", PullRequestStatus.OPEN, "2026-09-20T10:00:00Z", false, "develop", "bug");
        VcsPullRequest p3 = pr("p3", PullRequestStatus.MERGED, "2026-10-03T10:00:00Z", false, "main", "bug");
        p3.setMergedAt(Instant.parse("2026-10-04T10:00:00Z"));
        pullRequests.addAll(List.of(p1, p2, p3));

        TeamFlowV2Output.ReviewQueue queue = useCase.execute(ORG, PROJECT, "s2").reviewQueue();

        assertThat(queue.open()).isEqualTo(2);
        assertThat(queue.drafts()).isEqualTo(1);
        assertThat(queue.byBaseBranch()).containsExactly(new BranchCount("develop", 1), new BranchCount("main", 1));
        // labels of the PRs created in the sprint (p2 was created before it)
        assertThat(queue.labels()).containsExactly(new LabelCount("bug", 2), new LabelCount("ui", 1));
    }

    @Test
    void should_nullTheCurrentStateFields_when_sprintIsPast() {
        VcsPullRequest p1 = pr("p1", PullRequestStatus.OPEN, "2026-09-20T10:00:00Z", true, "main", "infra");
        pullRequests.add(p1);
        sprintItems("s1", item("t", ResolvedStatus.TODO, 3.0, "2026-09-10T00:00:00Z"));

        TeamFlowV2Output out = useCase.execute(ORG, PROJECT, "s1");

        assertThat(out.reviewQueue().open()).isNull();
        assertThat(out.reviewQueue().drafts()).isNull();
        assertThat(out.reviewQueue().byBaseBranch()).isEmpty();
        assertThat(out.reviewQueue().labels()).containsExactly(new LabelCount("infra", 1));
        assertThat(out.scope().atRiskSp()).isNull();
        assertThat(out.cfd()).hasSize(10);     // every working day of the past sprint
    }

    @Test
    void should_sumTheEffortNotDoneYet_inTheCurrentSprint() {
        sprintItems("s2", doneItem("d", "2026-10-01T00:00:00Z", "2026-10-01T10:00:00Z", "2026-10-02T10:00:00Z", 24.0),
                item("t", ResolvedStatus.TODO, 2.0, "2026-10-01T00:00:00Z"),
                item("i", ResolvedStatus.IN_PROGRESS, 5.0, "2026-10-01T00:00:00Z"));

        assertThat(useCase.execute(ORG, PROJECT, "s2").scope().atRiskSp()).isEqualTo(7.0);
    }

    @Test
    void should_returnEmptyListsAndNulls_when_sprintHasNoItems() {
        TeamFlowV2Output out = useCase.execute(ORG, PROJECT, "s2");

        assertThat(out.sprint().id()).isEqualTo("s2");
        assertThat(out.processTemplate()).isNull();
        assertThat(out.states()).isEmpty();
        assertThat(out.cfd()).isEmpty();
        assertThat(out.stateTimes().current().queueHours()).isEqualTo(DurationStat.empty());
        assertThat(out.stateTimes().current().inProgressHours()).isEqualTo(DurationStat.empty());
        assertThat(out.rework().reopened()).isEmpty();
        assertThat(out.rework().doneInWindow()).isZero();
        assertThat(out.rework().ratePct()).isNull();
        assertThat(out.scope().atRiskSp()).isNull();
        assertThat(out.reviewQueue().open()).isZero();
        assertThat(out.reviewQueue().drafts()).isZero();
        assertThat(out.reviewQueue().byBaseBranch()).isEmpty();
        assertThat(out.reviewQueue().labels()).isEmpty();
    }

    @Test
    void should_readTransitionsOnce_scopedByOrganization() {
        sprintItems("s2", item("x", ResolvedStatus.TODO, 1.0, "2026-10-01T00:00:00Z"));
        projectItem("r", ResolvedStatus.DONE, "2026-10-02T10:00:00Z");

        useCase.execute(ORG, PROJECT, "s2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(transitionRepository, times(1)).findActiveByOrganizationIdAndWorkItemIdIn(eq(ORG), ids.capture());
        assertThat(ids.getValue()).contains("x", "r");
    }

    @Test
    void should_readOnlyBoundedData_neverTheWholeProjectNorThePullRequestHistory() {
        sprintItems("s1", item("t", ResolvedStatus.TODO, 3.0, "2026-09-10T00:00:00Z"));

        useCase.execute(ORG, PROJECT, "s1");

        verify(workItemRepository, never()).findAllByOrganizationIdAndProjectId(anyString(), anyString());
        verify(workItemRepository, never()).findAllByOrganizationIdAndProjectIdAndSprintId(anyString(), anyString(), anyString());
        verify(workItemRepository, times(1)).findActiveByOrganizationIdAndProjectIdAndSprintIdIn(eq(ORG), eq(PROJECT),
                anyCollection());
        verify(vcsReadPort, never()).pullRequestsOfRepository(anyString(), anyString());
        verify(vcsReadPort, never()).openPullRequests(anyString(), anyCollection());   // past sprint: no current state
        verify(vcsReadPort, times(1)).pullRequestsCreatedIn(eq(ORG), anyCollection(), any(Instant.class), any(Instant.class));
    }

    private void sprintItems(String sprintId, WorkItem... items) {
        Arrays.stream(items).forEach(i -> i.setSprintId(sprintId));
        projectItems.addAll(List.of(items));
    }

    private WorkItem projectItem(String id, ResolvedStatus status, String completedAt) {
        WorkItem item = item(id, status, 1.0, "2026-09-01T00:00:00Z");
        item.setAssigneeUserId(ME);
        if (completedAt != null) item.setCompletedAt(Instant.parse(completedAt));
        projectItems.add(item);
        return item;
    }

    private void transition(String workItemId, ResolvedStatus to, String at) {
        transitions.add(new WorkItemTransition("t-" + transitions.size(), ORG, workItemId, null, to.name(), to,
                Instant.parse(at), Instant.parse(at), true, 0L, NOW, NOW));
    }

    private static WorkItem item(String id, ResolvedStatus status, Double effort, String createdAt) {
        WorkItem item = WorkItemFixture.item(id, WorkItem.ResolvedType.STORY, status, effort, null);
        item.setCreatedAt(Instant.parse(createdAt));
        return item;
    }

    private static WorkItem doneItem(String id, String createdAt, String startedAt, String completedAt, Double cycleHours) {
        WorkItem item = item(id, ResolvedStatus.DONE, 1.0, createdAt);
        item.setStartedAt(Instant.parse(startedAt));
        item.setCompletedAt(Instant.parse(completedAt));
        item.setCycleTimeHours(cycleHours);
        return item;
    }

    private static VcsPullRequest pr(String id, PullRequestStatus status, String createdAt, boolean draft,
                                     String baseBranch, String... labels) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, REPO, VcsProvider.GITHUB, "ext-" + id,
                Integer.parseInt(id.replaceAll("\\D", "")), "PR " + id, status);
        pr.setCreatedAt(Instant.parse(createdAt));
        pr.setDraft(draft);
        pr.setBaseBranch(baseBranch);
        pr.setLabels(List.of(labels));
        pr.setAuthorLogin("dev");
        return pr;
    }

    private static Sprint sprint(String id, String name, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", name,
                "\\Orion\\" + name, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static User user(String id, String name) {
        return new User(id, ORG, id + "@x.io", null, name, "role", null, null, null, null, null, true, true,
                0L, NOW, NOW);
    }
}
