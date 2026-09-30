package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures.LegacyReviewResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculatePullRequestMetricsUseCaseTest {

    private static final Instant CREATED = Instant.parse("2024-01-01T00:00:00Z");

    @Mock private VcsPullRequestRepository pullRequestRepository;
    @Mock private VcsReviewEventRepository reviewEventRepository;

    @InjectMocks
    private CalculatePullRequestMetricsUseCase useCase;

    @Test
    void should_computeCycleTimeFromMergedAt_when_merged() {
        givenPullRequests(prWith("pr-1", CREATED.plusSeconds(3600 * 5), null));

        useCase.execute("org-1", "repo-1");

        verify(pullRequestRepository).updateMetrics(eq("org-1"), eq("pr-1"), eq(5.0), any(), any());
    }

    @Test
    void should_computeCycleTimeFromClosedAt_when_closedWithoutMerge() {
        givenPullRequests(prWith("pr-2", null, CREATED.plusSeconds(3600 * 2)));

        useCase.execute("org-1", "repo-1");

        verify(pullRequestRepository).updateMetrics(eq("org-1"), eq("pr-2"), eq(2.0), any(), any());
    }

    @Test
    void should_leaveAllMetricsNull_when_openWithoutReviews() {
        givenPullRequests(prWith("pr-3", null, null));

        useCase.execute("org-1", "repo-1");

        ArgumentCaptor<Double> cycle = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> ttfr = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> ttfa = ArgumentCaptor.forClass(Double.class);
        verify(pullRequestRepository).updateMetrics(eq("org-1"), eq("pr-3"), cycle.capture(), ttfr.capture(), ttfa.capture());
        assertThat(cycle.getValue()).isNull();
        assertThat(ttfr.getValue()).isNull();
        assertThat(ttfa.getValue()).isNull();
    }

    @Test
    void should_computeFirstReviewAndFirstApprovalSeparately_when_commentPrecedesApproval() {
        givenPullRequests(prWith("pr-4", null, null));
        when(reviewEventRepository.findAllByOrganizationIdAndPullRequestIdIn(eq("org-1"), anyList()))
                .thenReturn(List.of(
                        review("pr-4", "alice", CREATED.plusSeconds(3600 * 20), LegacyReviewResult.APPROVED),
                        review("pr-4", "bob", CREATED.plusSeconds(3600 * 2), LegacyReviewResult.COMMENTED)));

        useCase.execute("org-1", "repo-1");

        verify(pullRequestRepository).updateMetrics(eq("org-1"), eq("pr-4"), any(), eq(2.0), eq(20.0));
    }

    @Test
    void should_ignoreAuthorAndBotReviews_when_computingTimeToFirstReview() {
        givenPullRequests(prWith("pr-5", null, null));
        VcsReviewEvent bot = review("pr-5", "dependabot[bot]", CREATED.plusSeconds(3600), LegacyReviewResult.COMMENTED);
        VcsReviewEvent author = review("pr-5", "octocat", CREATED.plusSeconds(1800), LegacyReviewResult.COMMENTED);
        VcsReviewEvent human = review("pr-5", "alice", CREATED.plusSeconds(3600 * 3), LegacyReviewResult.CHANGES_REQUESTED);
        when(reviewEventRepository.findAllByOrganizationIdAndPullRequestIdIn(eq("org-1"), anyList()))
                .thenReturn(List.of(bot, author, human));

        useCase.execute("org-1", "repo-1");

        verify(pullRequestRepository).updateMetrics(eq("org-1"), eq("pr-5"), any(), eq(3.0), any());
    }

    @Test
    void should_readReviewsInOneQuery_when_repositoryHasManyPullRequests() {
        givenPullRequests(prWith("pr-6", null, null), prWith("pr-7", null, null));

        useCase.execute("org-1", "repo-1");

        verify(reviewEventRepository).findAllByOrganizationIdAndPullRequestIdIn("org-1", List.of("pr-6", "pr-7"));
    }

    private void givenPullRequests(VcsPullRequest... prs) {
        when(pullRequestRepository.findAllByOrganizationIdAndRepositoryId("org-1", "repo-1")).thenReturn(List.of(prs));
    }

    private VcsPullRequest prWith(String id, Instant mergedAt, Instant closedAt) {
        return VcsLegacyFixtures.pullRequest(id, "org-1", "repo-1", "ext-" + id, 1, "title",
                PullRequestStatus.OPEN, "octocat", null, CREATED, mergedAt, closedAt, CREATED,
                null, null, true, Instant.now(), null);
    }

    private VcsReviewEvent review(String prId, String login, Instant submittedAt, LegacyReviewResult result) {
        return VcsLegacyFixtures.reviewEvent(null, "org-1", prId, "rev-" + login, login, null, result, submittedAt,
                true, Instant.now(), Instant.now(), null);
    }
}
