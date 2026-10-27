package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.ValidatedUserDay;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.dashboard.application.AiUsageSummaries;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Member;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.StatusByType;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.4/1.5 — team dashboard v2, table tests ported from the preview's
 * {@code buildTeam}: velocity, predictability, aging vs p85, status × type, members, stale PRs of
 * unlinked authors, repository health, and the AI cost basis (allocated only — the "add the
 * unallocated cost to the team cost" mutation must fail here).
 * Now = Thursday 2026-10-08 12:00Z; the current sprint runs 2026-10-01 → 2026-10-14.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetTeamDashboardV2UseCaseTest {

    private static final String ORG = WorkItemFixture.ORG;
    private static final String PROJECT = WorkItemFixture.PROJECT;
    private static final String ME = "user-a";
    private static final String OTHER = "user-b";
    private static final String REPO = "repo-1";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final LocalDate S2_START = LocalDate.parse("2026-10-01");
    private static final LocalDate S2_END = LocalDate.parse("2026-10-14");
    private static final Sprint S1 = sprint("s1", "Sprint 1", Sprint.TimeFrame.PAST, "2026-09-17", "2026-09-30");
    private static final Sprint S2 = sprint("s2", "Sprint 2", Sprint.TimeFrame.CURRENT, "2026-10-01", "2026-10-14");

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConnectorTokenRepository connectorTokenRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private VcsReadPort vcsReadPort;
    @Mock private VcsCommitRepository commitRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private ListProjectRepositoriesUseCase listProjectRepositoriesUseCase;

    private GetTeamDashboardV2UseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTeamDashboardV2UseCase(projectRepository,
                new DashboardPeopleDirectory(userRepository, connectorTokenRepository),
                new DashboardSprintResolver(sprintRepository), new ProjectVcsActivity(vcsReadPort, commitRepository),
                workItemRepository, new EffortBearingLevelResolver(),
                new ProjectAiCostCalculator(allocationRepository, aiUsageRepository), aiUsageRepository,
                allocationRepository, listProjectRepositoriesUseCase, Clock.fixed(NOW, ZoneOffset.UTC));

        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "Orion", "orion", null, true, List.of(ME, OTHER), 0L, NOW, NOW)));
        when(userRepository.findAllByOrganizationId(ORG)).thenReturn(List.of(
                user(ME, "Ana Ribeiro", "gh-ana"), user(OTHER, "Bruno Lima", null)));
        when(connectorTokenRepository.findActiveByOrganizationIdAndUserId(ORG, ME))
                .thenReturn(List.of(new ConnectorToken("t1", ORG, ME, "laptop", "hash", true, 0L, NOW, NOW)));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(S1, S2));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s2", ORG)).thenReturn(Optional.of(S2));
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(
                new VcsRepository(REPO, ORG, VcsProvider.GITHUB, "ext-repo", "owner/orion")));
    }

    @Test
    void should_throwSprintNotFound_when_sprintIsOfAnotherProject() {
        Sprint foreign = sprint("s-x", "Sprint X", Sprint.TimeFrame.CURRENT, "2026-10-01", "2026-10-14");
        foreign.setProjectId("project-2");
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s-x", ORG)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> useCase.execute(ORG, ME, PROJECT, "s-x")).isInstanceOf(SprintNotFoundException.class);
    }

    @Test
    void should_computeVelocity_andPredictability_perSprint() {
        sprintItems("s1", done("a1", 3.0, 10.0), done("a2", 2.0, 30.0), todo("a3", 5.0));
        sprintItems("s2", done("b1", 3.0, 8.0), done("b2", 5.0, 16.0), todo("b3", 2.0));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().spPlanned()).isEqualTo(10.0);
        assertThat(out.kpis().spDelivered()).isEqualTo(8.0);
        assertThat(out.kpis().itemsDone()).isEqualTo(2);
        assertThat(out.kpis().itemsTotal()).isEqualTo(3);
        assertThat(out.kpis().cycleTimeHours()).isEqualTo(new DurationStat(8.0, 16.0, 2));
        assertThat(out.previous().spDelivered()).isEqualTo(5.0);
        assertThat(out.history()).extracting(h -> h.predictabilityPct()).containsExactly(50.0, 80.0);
        assertThat(out.history().get(1).isSelected()).isTrue();
        assertThat(out.history().get(1).label()).isEqualTo("S2*");
        assertThat(out.sprint().id()).isEqualTo("s2");
        assertThat(out.team()).isEqualTo(new TeamDashboardV2Output.TeamInfo("Core", 2));
    }

    /** complexity-effort 3.1: Story 3 pts (Tasks 4 h + 8 h), Bug without points, cost US$ 6. */
    @Test
    void should_reportComplexityEffortAndAiRatios_fromTheCommonFixture() {
        WorkItem story = done("st", 20.0, 5.0);
        story.setComplexity(3.0);
        story.setAssigneeUserId(ME);
        WorkItem task1 = WorkItemFixture.item("t1", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 4.0, "ext-st");
        WorkItem task2 = WorkItemFixture.item("t2", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE, 8.0, "ext-st");
        WorkItem bug = WorkItemFixture.item("bg", WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.TODO);
        List<WorkItem> all = List.of(story, task1, task2, bug);
        sprintItems("s2", all.toArray(WorkItem[]::new));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(all);
        AIUsage allocated = usage("u1", ME, "2026-10-02", "a1", "6.00", "claude");
        AiUsageAllocation a1 = new AiUsageAllocation("a1", ORG, ME, LocalDate.parse("2026-10-02"), List.of("u1"),
                List.of(new AllocationSplit("st", "Story", BigDecimal.ONE)),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(
                eq(ORG), anyCollection(), eq(S2_START), eq(S2_END))).thenReturn(List.of(a1));
        when(aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(List.of(allocated));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().complexityPlanned()).isEqualTo(3.0);
        assertThat(out.kpis().complexityDelivered()).isEqualTo(3.0);
        // the Tasks' hours only: the Story's own 20 is never summed (mutation: including it)
        assertThat(out.kpis().effortLogged()).isEqualTo(12.0);
        assertThat(out.kpis().itemsWithComplexity()).isEqualTo(1);
        assertThat(out.kpis().aiCostPerPoint()).isEqualByComparingTo("2");
        assertThat(out.kpis().aiCostPerHour()).isEqualByComparingTo("0.5");
        assertThat(out.history().get(1).complexityDelivered()).isEqualTo(3.0);
        assertThat(out.history().get(1).aiCostPerPoint()).isEqualByComparingTo("2");
        assertThat(out.history().get(1).effortLogged()).isEqualTo(12.0);
        assertThat(out.history().get(1).hoursPerPoint()).isEqualTo(4.0);
        assertThat(out.history().get(0).hoursPerPoint()).as("a sprint without delivered complexity").isNull();
        assertThat(member(out, ME).complexityDelivered()).isEqualTo(3.0);
        assertThat(member(out, OTHER).complexityDelivered()).isNull();
        // old fields keep their values (D6)
        assertThat(out.kpis().spDelivered()).isNotNull();
    }

    @Test
    void should_leaveRatiosNull_notDivideByZero_when_noComplexityNorEffortWasDelivered() {
        WorkItem story = todo("st", 5.0);
        sprintItems("s2", story);
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(story));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().complexityDelivered()).isEqualTo(0.0);
        assertThat(out.kpis().effortLogged()).isNull();
        assertThat(out.kpis().aiCostPerPoint()).isNull();
        assertThat(out.kpis().aiCostPerHour()).isNull();
        assertThat(out.history()).allSatisfy(h -> assertThat(h.hoursPerPoint()).isNull());
    }

    @Test
    void should_returnNulls_notZeros_when_sprintHasNoItems() {
        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().spPlanned()).isNull();
        assertThat(out.kpis().spDelivered()).isNull();
        assertThat(out.kpis().itemsTotal()).isZero();
        assertThat(out.kpis().aiCostPerSp()).isNull();
        assertThat(out.kpis().cycleTimeHours()).isEqualTo(DurationStat.empty());
        assertThat(out.history()).allSatisfy(h -> assertThat(h.predictabilityPct()).isNull());
        assertThat(out.burndown()).isNotEmpty().allSatisfy(d -> assertThat(d.remaining()).isNull());
        assertThat(out.agingItems()).isEmpty();
        assertThat(out.p85CycleHours()).isNull();
        assertThat(out.statusByType()).allSatisfy(s -> assertThat(s.todo() + s.inProgress() + s.done()).isZero());
    }

    @Test
    void should_listAgingItems_byDaysInProgress_withTheP85OfPreviousSprints() {
        List<WorkItem> previousDone = new ArrayList<>();
        for (int i = 1; i <= 10; i++) previousDone.add(done("p" + i, 1.0, i * 10.0));
        sprintItems("s1", previousDone.toArray(WorkItem[]::new));
        WorkItem old = inProgress("b1", "2026-10-03T12:00:00Z");
        old.setComplexity(5.0);
        WorkItem fresh = inProgress("b2", "2026-10-07T12:00:00Z");
        sprintItems("s2", fresh, old);

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.p85CycleHours()).isEqualTo(90.0);
        assertThat(out.agingItems()).extracting(ItemView::id).containsExactly("b1", "b2");
        assertThat(out.agingItems().get(0).daysInProgress()).isEqualTo(5.0);
        assertThat(out.agingItems().get(0).complexity()).isEqualTo(5.0);
        assertThat(out.agingItems().get(1).complexity()).isNull();
    }

    @Test
    void should_ageTheStoryWithRolledUpHours_notItsTasks_andKeepAnOrphanTask() {
        WorkItem story = inProgress("st", "2026-10-03T12:00:00Z");
        story.setEffort(null);
        story.setComplexity(3.0);
        WorkItem t1 = WorkItemFixture.item("t1", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.IN_PROGRESS, 4.0, "ext-st");
        t1.setStartedAt(Instant.parse("2026-10-04T12:00:00Z"));
        WorkItem t2 = WorkItemFixture.item("t2", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.IN_PROGRESS, 8.0, "ext-st");
        t2.setStartedAt(Instant.parse("2026-10-04T12:00:00Z"));
        WorkItem orphan = WorkItemFixture.item("or", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.IN_PROGRESS, 2.0, null);
        orphan.setStartedAt(Instant.parse("2026-10-05T12:00:00Z"));
        sprintItems("s2", story, t1, t2, orphan);

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.agingItems()).extracting(ItemView::id).containsExactly("st", "or");
        assertThat(out.agingItems().get(0).complexity()).isEqualTo(3.0);
        assertThat(out.agingItems().get(0).effortRolledUp()).isEqualTo(12.0);
    }

    @Test
    void should_countStatusByType() {
        WorkItem bug = WorkItemFixture.item("c4", WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.IN_PROGRESS);
        WorkItem task = WorkItemFixture.item("c5", WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.TODO);
        sprintItems("s2", done("c1", 1.0, 1.0), done("c2", 1.0, 1.0), todo("c3", 1.0), bug, task);

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.statusByType()).startsWith(
                new StatusByType("STORY", 1, 0, 2), new StatusByType("BUG", 0, 1, 0), new StatusByType("TASK", 1, 0, 0));
    }

    @Test
    void should_reportOnlyAllocatedAiCost_asTeamCost_andKeepUnallocatedApart() {
        sprintItems("s2", done("wi-in", 5.0, 4.0));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(done("wi-in", 5.0, 4.0)));
        AIUsage allocated = usage("u1", ME, "2026-10-02", "a1", "10.00", "claude");
        AIUsage unallocated = usage("u9", ME, "2026-10-03", null, "7.00", "gpt-5");
        AiUsageAllocation a1 = new AiUsageAllocation("a1", ORG, ME, LocalDate.parse("2026-10-02"), List.of("u1"),
                List.of(new AllocationSplit("wi-in", "In", new BigDecimal("0.50")),
                        new AllocationSplit("wi-out", "Out", new BigDecimal("0.50"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, NOW, NOW);
        when(allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(
                eq(ORG), anyCollection(), eq(S2_START), eq(S2_END))).thenReturn(List.of(a1));
        when(allocationRepository.findValidatedUserDays(eq(ORG), anyCollection(), eq(S2_START), eq(S2_END)))
                .thenReturn(List.of(new ValidatedUserDay(ME, LocalDate.parse("2026-10-02"))));
        when(aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(eq(ORG), anyCollection())).thenReturn(List.of(allocated));
        when(aiUsageRepository.summarizeByUserAndDay(eq(ORG), anyCollection(), eq(S2_START), eq(S2_END)))
                .thenReturn(AiUsageSummaries.of(List.of(allocated, unallocated)));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("5.00");
        assertThat(out.kpis().unallocatedAiCostUsd()).isEqualByComparingTo("7.00");
        assertThat(out.kpis().aiCostPerSp()).isEqualByComparingTo("1.00");
        assertThat(out.ai().unallocatedCostUsd()).isEqualByComparingTo("7.00");
        assertThat(out.ai().byModel()).extracting(AiModel::model).containsExactly("claude");
        assertThat(out.ai().byModel().stream().map(AiModel::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(out.kpis().aiCostUsd());
        assertThat(out.ai().byDay().get(1).date()).isEqualTo("2026-10-02");
        assertThat(out.ai().byDay().get(1).costUsd()).isEqualByComparingTo("5.00");
        assertThat(out.ai().membersWithUsage()).isEqualTo(1);
        assertThat(out.ai().allocation().validatedDays()).isEqualTo(1);
        assertThat(out.ai().allocation().unallocatedDays()).isEqualTo(1);
        assertThat(member(out, ME).aiCostUsd()).isEqualByComparingTo("5.00");
        assertThat(member(out, OTHER).aiCostUsd()).isNull();
        // 100 + 50 tokens of the allocated record, 50% of it carried by the project; the unallocated day is out
        assertThat(out.history().get(1).aiTokens()).isEqualTo(75L);
        assertThat(out.history().get(0).aiTokens()).isNull();
    }

    @Test
    void should_returnNullAiCost_inKpisPreviousAndHistory_when_noMemberHasUsageInTheWindow() {
        sprintItems("s1", done("a1", 3.0, 10.0));
        sprintItems("s2", done("b1", 5.0, 8.0));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().aiCostUsd()).isNull();
        assertThat(out.kpis().aiCostPerSp()).isNull();
        assertThat(out.kpis().unallocatedAiCostUsd()).isNull();
        assertThat(out.previous().aiCostUsd()).isNull();
        assertThat(out.previous().aiCostPerSp()).isNull();
        assertThat(out.history()).allSatisfy(h -> {
            assertThat(h.aiCostUsd()).isNull();
            assertThat(h.aiCostPerSp()).isNull();
            assertThat(h.aiTokens()).isNull();
        });
        assertThat(out.ai().unallocatedCostUsd()).isNull();
    }

    @Test
    void should_keepZeroAllocatedCost_when_membersHaveUsageButNothingIsAllocatedToTheProject() {
        sprintItems("s2", done("b1", 5.0, 8.0));
        AIUsage unallocated = usage("u9", ME, "2026-10-03", null, "7.00", "gpt-5");
        when(aiUsageRepository.summarizeByUserAndDay(eq(ORG), anyCollection(), eq(S2_START), eq(S2_END)))
                .thenReturn(AiUsageSummaries.of(List.of(unallocated)));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.kpis().aiCostUsd()).isEqualByComparingTo("0");
        assertThat(out.kpis().aiCostPerSp()).isEqualByComparingTo("0");
        assertThat(out.kpis().unallocatedAiCostUsd()).isEqualByComparingTo("7.00");
        assertThat(out.history().get(1).aiCostUsd()).isEqualByComparingTo("0");
        assertThat(out.history().get(0).aiCostUsd()).isNull();
        assertThat(out.history().get(1).aiTokens()).isNull();
        assertThat(out.ai().unallocatedCostUsd()).isEqualByComparingTo("7.00");
    }

    @Test
    void should_labelStalePrOfUnlinkedAuthorByLogin_andNotCreditAnyMember() {
        VcsPullRequest unlinked = pr("pr-7", null, "2026-10-02T10:00:00Z");
        unlinked.setAuthorLogin("ghost");
        VcsPullRequest mine = pr("pr-8", ME, "2026-10-07T20:00:00Z");
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(unlinked, mine));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.stalePrs()).hasSize(1);
        assertThat(out.stalePrs().get(0).author()).isEqualTo("@ghost");
        assertThat(out.stalePrs().get(0).authorLinked()).isFalse();
        assertThat(out.kpis().openPrs()).isEqualTo(2);
        assertThat(out.kpis().stalePrs()).isEqualTo(1);
        assertThat(member(out, ME).prsOpened()).isEqualTo(1);
        assertThat(member(out, OTHER).prsOpened()).isNull();
    }

    @Test
    void should_notCountMergedOrClosedPrsWithoutReview_asStaleOrOpen() {
        VcsPullRequest merged = pr("pr-5", ME, "2026-10-02T10:00:00Z");
        merged.setStatus(PullRequestStatus.MERGED);
        merged.setMergedAt(Instant.parse("2026-10-03T10:00:00Z"));
        VcsPullRequest closed = pr("pr-6", ME, "2026-10-02T10:00:00Z");
        closed.setStatus(PullRequestStatus.CLOSED);
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(merged, closed));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        assertThat(out.stalePrs()).isEmpty();
        assertThat(out.kpis().stalePrs()).isEqualTo(0);
        assertThat(out.kpis().openPrs()).isEqualTo(0);
    }

    @Test
    void should_buildMembers_fromProjectMembersAndAssignees_withReviewLoad() {
        WorkItem mine = done("d1", 3.0, 6.0);
        mine.setAssigneeUserId(ME);
        WorkItem wip = inProgress("d2", "2026-10-06T00:00:00Z");
        wip.setAssigneeUserId(ME);
        sprintItems("s2", mine, wip);
        VcsPullRequest othersPr = pr("pr-3", OTHER, "2026-10-02T10:00:00Z");
        when(vcsReadPort.pullRequestsOfRepository(ORG, REPO)).thenReturn(List.of(othersPr));
        VcsReviewEvent review = new VcsReviewEvent("r1", ORG, "pr-3", REPO, VcsProvider.GITHUB, "ext-r1",
                ReviewKind.APPROVED, Instant.parse("2026-10-03T10:00:00Z"));
        review.setActorUserId(ME);
        when(vcsReadPort.reviewEvents(eq(ORG), any())).thenReturn(List.of(review));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        Member me = member(out, ME);
        assertThat(me.isMe()).isTrue();
        assertThat(me.itemsDone()).isEqualTo(1);
        assertThat(me.itemsTotal()).isEqualTo(2);
        assertThat(me.spDelivered()).isEqualTo(3.0);
        assertThat(me.wip()).isEqualTo(1);
        assertThat(me.reviewsGiven()).isEqualTo(1);
        Member other = member(out, OTHER);
        assertThat(other.itemsTotal()).isZero();
        assertThat(other.spPlanned()).isNull();
        assertThat(other.reviewsGiven()).isNull();
        assertThat(out.members().get(0).userId()).isEqualTo(ME);
    }

    @Test
    void should_mapRepositoryHealth_withCommitsOfTheSprint() {
        when(listProjectRepositoriesUseCase.execute(PROJECT, S2_START, LocalDate.parse("2026-10-08"))).thenReturn(List.of(
                new ProjectRepositoryOutput(REPO, "owner/orion", 2, 1, null, null, null, null, null,
                        new DurationStat(5.0, 9.0, 4), ReviewMetrics.empty(), DeliveryMetrics.unavailable(),
                        REPO, VcsProvider.GITHUB, "owner/orion", "main")));
        VcsCommit commit = new VcsCommit("c1", ORG, REPO, VcsProvider.GITHUB, "c1");
        commit.setAuthoredAt(Instant.parse("2026-10-02T10:00:00Z"));
        when(commitRepository.findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(eq(ORG), any(), any(), any()))
                .thenReturn(List.of(commit));

        TeamDashboardV2Output out = useCase.execute(ORG, ME, PROJECT, "s2");

        var repo = out.repos().get(0);
        assertThat(repo.displayName()).isEqualTo("owner/orion");
        assertThat(repo.openPrs()).isEqualTo(2);
        assertThat(repo.stalePrs()).isEqualTo(1);
        assertThat(repo.prCycleTimeHours()).isEqualTo(new DurationStat(5.0, 9.0, 4));
        assertThat(repo.commits()).isEqualTo(1);
        assertThat(out.commitsByDay()).hasSize(8);
        assertThat(out.commitsByDay().get(1).commits()).isEqualTo(1);
    }

    private void sprintItems(String sprintId, WorkItem... items) {
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, sprintId))
                .thenReturn(List.of(items));
    }

    private static Member member(TeamDashboardV2Output out, String userId) {
        return out.members().stream().filter(m -> m.userId().equals(userId)).findFirst().orElseThrow();
    }

    private static WorkItem done(String id, Double effort, Double cycleHours) {
        WorkItem item = WorkItemFixture.item(id, WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE, effort, null);
        item.setCycleTimeHours(cycleHours);
        item.setComplexity(effort);
        item.setCompletedAt(Instant.parse("2026-10-02T12:00:00Z"));
        return item;
    }

    private static WorkItem todo(String id, Double effort) {
        WorkItem item = WorkItemFixture.item(id, WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO, effort, null);
        item.setComplexity(effort);
        return item;
    }

    private static WorkItem inProgress(String id, String startedAt) {
        WorkItem item = WorkItemFixture.item(id, WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.IN_PROGRESS, 1.0, null);
        item.setStartedAt(Instant.parse(startedAt));
        return item;
    }

    private static Sprint sprint(String id, String name, Sprint.TimeFrame timeFrame, String start, String end) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "ext-" + id, "team-1", "Core", name,
                "\\Orion\\" + name, Instant.parse(start + "T00:00:00Z"), Instant.parse(end + "T00:00:00Z"), timeFrame,
                true, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static User user(String id, String name, String githubId) {
        return new User(id, ORG, id + "@x.io", null, name, "role", null, githubId, null, null, null, true, true,
                0L, NOW, NOW);
    }

    private static VcsPullRequest pr(String id, String authorUserId, String createdAt) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, REPO, VcsProvider.GITHUB, "ext-" + id,
                Integer.parseInt(id.replaceAll("\\D", "")), "PR " + id, PullRequestStatus.OPEN);
        pr.setAuthorUserId(authorUserId);
        pr.setAuthorLogin(authorUserId == null ? "ghost" : authorUserId);
        pr.setCreatedAt(Instant.parse(createdAt));
        return pr;
    }

    private static AIUsage usage(String id, String userId, String day, String allocationId, String cost, String model) {
        return new AIUsage(id, ORG, userId, "gh", "ds", LocalDate.parse(day), model, "w", "w", "m", "m",
                100, 50, 2, "vscode", allocationId, new BigDecimal(cost), NOW, true, 0L, NOW, NOW);
    }
}
