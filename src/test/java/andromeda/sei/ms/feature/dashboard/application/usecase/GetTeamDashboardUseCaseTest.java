package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTeamDashboardUseCaseTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private UserRepository userRepository;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;
    @Mock private GithubRepoRepository githubRepoRepository;
    @Mock private GithubPullRequestRepository githubPullRequestRepository;

    private GetTeamDashboardUseCase useCase;

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String SPRINT_ID = "sprint-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        useCase = new GetTeamDashboardUseCase(sprintRepository, workItemRepository,
                new andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver(),
                aiUsageRepository, userRepository, githubRepoBindingRepository,
                githubRepoRepository, githubPullRequestRepository);
        lenient().when(userRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
        lenient().when(aiUsageRepository.findTotalCostByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of());
        lenient().when(githubRepoBindingRepository
                        .findAllByOrganizationIdAndProjectIdAndActiveTrue(any(), any()))
                .thenReturn(List.of());
    }

    // ── Task 4.8/4.9/4.10: stale PRs ─────────────────────────────────────

    @Test
    void should_includeStalePr_when_openMoreThan24hWithNoReview() {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/hello")));
        // BUG 1 fix: the binding's "owner/name" fullName must be resolved to the github_repos
        // Mongo id BEFORE querying github_pull_requests (children are keyed by Mongo id).
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG, List.of("octo/hello")))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/hello")));

        GithubPullRequest stalePr = pullRequest("pr-1", PullRequestStatus.OPEN,
                NOW.minusSeconds(48 * 3600), null);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoIdInAndStatus(
                ORG, List.of("mongo-repo-1"), PullRequestStatus.OPEN))
                .thenReturn(List.of(stalePr));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).hasSize(1);
        assertThat(output.stalePrs().get(0).prId()).isEqualTo("pr-1");
        assertThat(output.stalePrs().get(0).hoursOpen()).isGreaterThanOrEqualTo(48.0);
    }

    @Test
    void should_excludePr_when_anyReviewTypeAlreadyPresent_notJustApproved() {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/hello")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG, List.of("octo/hello")))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/hello")));

        // timeToFirstReviewHours is non-null the moment ANY review exists, regardless of its
        // result (approve/comment/changes-requested) — see CalculatePrMetricsUseCase.
        GithubPullRequest reviewedPr = pullRequest("pr-2", PullRequestStatus.OPEN,
                NOW.minusSeconds(48 * 3600), 10.0);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoIdInAndStatus(
                ORG, List.of("mongo-repo-1"), PullRequestStatus.OPEN))
                .thenReturn(List.of(reviewedPr));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).isEmpty();
    }

    @Test
    void should_returnEmptyStalePrs_notError_when_projectHasNoBoundRepos() {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of());

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).isEmpty();
    }

    @Test
    void should_returnEmptyStalePrs_when_boundFullNameNeverSyncedToGithubRepos() {
        // BUG 1 fix: an unresolvable fullName is skipped (correctly empty, not accidentally).
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/never-synced")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG, List.of("octo/never-synced")))
                .thenReturn(List.of());

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).isEmpty();
    }

    @Test
    void should_resolveAuthorName_viaLinkedAuthorUserId_beforeFallingBackToLogin() {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/hello")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG, List.of("octo/hello")))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/hello")));
        when(userRepository.findAllByOrganizationId(ORG))
                .thenReturn(List.of(new User("user-42", ORG, "dev@example.com", "hash",
                        "Linked Dev", null, null, "58421", null, null, null, true, true, 1L, NOW, NOW)));

        GithubPullRequest linkedPr = new GithubPullRequest("pr-1", ORG, "mongo-repo-1", "ext-pr-1",
                1, "Linked PR", PullRequestStatus.OPEN, "octocat", "user-42",
                NOW.minusSeconds(48 * 3600), null, null, NOW.minusSeconds(48 * 3600),
                null, null, true, NOW, 1L);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoIdInAndStatus(
                ORG, List.of("mongo-repo-1"), PullRequestStatus.OPEN))
                .thenReturn(List.of(linkedPr));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).hasSize(1);
        assertThat(output.stalePrs().get(0).authorName()).isEqualTo("Linked Dev");
    }

    @Test
    void should_fallBackToGithubLogin_when_authorHasNoLinkedUser() {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/hello")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                ORG, List.of("octo/hello")))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/hello")));

        GithubPullRequest unlinkedPr = new GithubPullRequest("pr-1", ORG, "mongo-repo-1", "ext-pr-1",
                1, "Unlinked PR", PullRequestStatus.OPEN, "octocat", null,
                NOW.minusSeconds(48 * 3600), null, null, NOW.minusSeconds(48 * 3600),
                null, null, true, NOW, 1L);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoIdInAndStatus(
                ORG, List.of("mongo-repo-1"), PullRequestStatus.OPEN))
                .thenReturn(List.of(unlinkedPr));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).hasSize(1);
        assertThat(output.stalePrs().get(0).authorName()).isEqualTo("octocat");
    }

    private GithubRepoBinding binding(String repoId) {
        return new GithubRepoBinding("binding-1", ORG, PROJECT, repoId, true, null, true, NOW, NOW, 1L);
    }

    private GithubRepo repo(String id, String fullName) {
        return new GithubRepo(id, ORG, "ext-" + id, "octo", "hello", fullName, NOW, true, NOW, NOW, 1L);
    }

    private GithubPullRequest pullRequest(String id, PullRequestStatus status, Instant createdAt,
                                           Double timeToFirstReviewHours) {
        return new GithubPullRequest(id, ORG, "mongo-repo-1", "ext-" + id, 1, "PR " + id, status,
                "gh-author", null, createdAt, null, null, createdAt,
                null, timeToFirstReviewHours, true, NOW, 1L);
    }

    @Test
    void should_includeFeatureTypedWorkItem_inMemberVelocity_notDropIt() {
        Sprint sprint = new Sprint(SPRINT_ID, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                NOW.minusSeconds(864000), NOW.plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, NOW, NOW);
        when(sprintRepository.findById(SPRINT_ID))
                .thenReturn(Optional.of(sprint));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(sprint));

        WorkItem feature = new WorkItem("wi-feat", ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "wi-feat", NOW, "Feature Title",
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Closed", WorkItem.ResolvedStatus.DONE,
                "Medium", 8.0, List.of(),
                SPRINT_ID, "ext-user", "user-1", null, null,
                NOW, null, NOW,
                null, null, null, true, 1L, NOW, null, null, null, null, 8.0, null, null, null);

        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(feature));
        when(userRepository.findAllByOrganizationId(ORG))
                .thenReturn(List.of(new User("user-1", ORG, "dev1@example.com", "hash",
                        "Dev One", null, null, null, null, null,
                        null, true, true, 1L, NOW, NOW)));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, SPRINT_ID, null, null);

        // A FEATURE-typed item must be counted like a STORY here, not silently dropped (0 members).
        assertThat(output.members()).hasSize(1);
        assertThat(output.members().get(0).storyPointsDelivered()).isEqualTo(8.0);
        assertThat(output.teamVelocity()).isEqualTo(8.0);
    }

    // ── dashboard-effort-ai-comparison: velocityTrend com planejado + tokens ──

    @Test
    void should_carrySpPlannedAndTotalTokens_inVelocityTrend() {
        Sprint sprint = sprint(SPRINT_ID, "Sprint 25", Sprint.TimeFrame.CURRENT,
                NOW.minusSeconds(864000), NOW.plusSeconds(86400));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(
                        storyItem("wi-1", SPRINT_ID, "user-1", WorkItem.ResolvedStatus.DONE, 13.0),
                        storyItem("wi-2", SPRINT_ID, "user-1", WorkItem.ResolvedStatus.DONE, 9.0),
                        storyItem("wi-3", SPRINT_ID, "user-1", WorkItem.ResolvedStatus.IN_PROGRESS, 8.0)));
        when(aiUsageRepository.findTotalCostByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of("user-1", new BigDecimal("0.72")));
        when(aiUsageRepository.findTotalTokensByUserIdsAndDateRange(any(), any(), any(), any()))
                .thenReturn(Map.of("user-1", 9000L));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.velocityTrend()).hasSize(1);
        TeamDashboardOutput.VelocityPoint point = output.velocityTrend().get(0);
        // planned sums every story-tier item regardless of status; delivered stays DONE-only.
        assertThat(point.spPlanned()).isEqualTo(30.0);
        assertThat(point.storyPoints()).isEqualTo(22.0);
        assertThat(point.totalTokens()).isEqualTo(9000L);
        assertThat(point.aiCostUsd()).isEqualByComparingTo("0.72");
    }

    @Test
    void should_nullTokensAndCost_inVelocityTrend_whenSprintHasNoDates() {
        Sprint sprint = sprint(SPRINT_ID, "Sprint 26", Sprint.TimeFrame.PAST, null, null);
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, PROJECT, SPRINT_ID))
                .thenReturn(List.of(
                        storyItem("wi-1", SPRINT_ID, "user-1", WorkItem.ResolvedStatus.DONE, 5.0)));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        TeamDashboardOutput.VelocityPoint point = output.velocityTrend().get(0);
        assertThat(point.spPlanned()).isEqualTo(5.0);
        assertThat(point.storyPoints()).isEqualTo(5.0);
        assertThat(point.totalTokens()).isNull();
        assertThat(point.aiCostUsd()).isNull();
    }

    private Sprint sprint(String id, String name, Sprint.TimeFrame frame,
                          Instant startDate, Instant endDate) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "ext-" + id, "team-1", "Team A", name, "/path",
                startDate, endDate, frame, true, 1L, NOW, NOW);
    }

    private WorkItem storyItem(String id, String sprintId, String userId,
                               WorkItem.ResolvedStatus status, double effort) {
        return new WorkItem(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                id, NOW, "Item " + id,
                "Story", WorkItem.ResolvedType.STORY,
                status.name(), status,
                "Medium", effort, List.of(),
                sprintId, "ext-" + userId, userId, null, null,
                NOW, null, status == WorkItem.ResolvedStatus.DONE ? NOW : null,
                null, null, null, true, 1L, NOW,
                null, null, null, null, effort, null, null, null);
    }
}
