package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.TeamDashboardOutput;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
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
                aiUsageRepository, userRepository, githubRepoBindingRepository, githubPullRequestRepository);
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
                .thenReturn(List.of(binding("repo-1")));

        GithubPullRequest stalePr = pullRequest("pr-1", PullRequestStatus.OPEN,
                NOW.minusSeconds(48 * 3600), null);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoIdInAndStatus(
                ORG, List.of("repo-1"), PullRequestStatus.OPEN))
                .thenReturn(List.of(stalePr));

        TeamDashboardOutput output = useCase.execute(ORG, "auth-user", PROJECT, null, null, null);

        assertThat(output.stalePrs()).hasSize(1);
        assertThat(output.stalePrs().get(0).prId()).isEqualTo("pr-1");
        assertThat(output.stalePrs().get(0).hoursOpen()).isGreaterThanOrEqualTo(48.0);
    }

    @Test
    void should_excludePr_when_anyReviewTypeAlreadyPresent_notJustApproved() {
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("repo-1")));

        // timeToFirstReviewHours is non-null the moment ANY review exists, regardless of its
        // result (approve/comment/changes-requested) — see CalculatePrMetricsUseCase.
        GithubPullRequest reviewedPr = pullRequest("pr-2", PullRequestStatus.OPEN,
                NOW.minusSeconds(48 * 3600), 10.0);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoIdInAndStatus(
                ORG, List.of("repo-1"), PullRequestStatus.OPEN))
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

    private GithubRepoBinding binding(String repoId) {
        return new GithubRepoBinding("binding-1", ORG, PROJECT, repoId, true, null, true, NOW, NOW, 1L);
    }

    private GithubPullRequest pullRequest(String id, PullRequestStatus status, Instant createdAt,
                                           Double timeToFirstReviewHours) {
        return new GithubPullRequest(id, ORG, "repo-1", "ext-" + id, 1, "PR " + id, status,
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
}
