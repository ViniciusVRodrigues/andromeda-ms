package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubReview;
import andromeda.sei.ms.feature.github.domain.GithubReviewRepository;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.domain.ReviewResult;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculatePrMetricsUseCaseTest {

    @Mock private GithubPullRequestRepository githubPullRequestRepository;
    @Mock private GithubReviewRepository githubReviewRepository;

    @InjectMocks
    private CalculatePrMetricsUseCase useCase;

    @Test
    void shouldComputeCycleTime_usingMergedAt_whenMerged() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant mergedAt = createdAt.plusSeconds(3600 * 5); // 5 hours later
        GithubPullRequest pr = prWith("pr-1", createdAt, mergedAt, null);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoId("org-1", "repo-1")).thenReturn(List.of(pr));
        when(githubReviewRepository.findAllByOrganizationIdAndPullRequestId("org-1", "pr-1")).thenReturn(List.of());

        useCase.execute("org-1", "repo-1");

        verify(githubPullRequestRepository).updateMetrics(eq("pr-1"), eq(5.0), any());
    }

    @Test
    void shouldComputeCycleTime_usingClosedAt_whenClosedWithoutMerge() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        Instant closedAt = createdAt.plusSeconds(3600 * 2); // 2 hours later
        GithubPullRequest pr = prWith("pr-2", createdAt, null, closedAt);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoId("org-1", "repo-1")).thenReturn(List.of(pr));
        when(githubReviewRepository.findAllByOrganizationIdAndPullRequestId("org-1", "pr-2")).thenReturn(List.of());

        useCase.execute("org-1", "repo-1");

        verify(githubPullRequestRepository).updateMetrics(eq("pr-2"), eq(2.0), any());
    }

    @Test
    void shouldLeaveCycleTimeNull_whenStillOpen() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        GithubPullRequest pr = prWith("pr-3", createdAt, null, null);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoId("org-1", "repo-1")).thenReturn(List.of(pr));
        when(githubReviewRepository.findAllByOrganizationIdAndPullRequestId("org-1", "pr-3")).thenReturn(List.of());

        useCase.execute("org-1", "repo-1");

        ArgumentCaptor<Double> cycleTimeCaptor = ArgumentCaptor.forClass(Double.class);
        verify(githubPullRequestRepository).updateMetrics(eq("pr-3"), cycleTimeCaptor.capture(), any());
        assertThat(cycleTimeCaptor.getValue()).isNull();
    }

    @Test
    void shouldComputeTimeToFirstReview_usingEarliestReviewAcrossAllStates() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        GithubPullRequest pr = prWith("pr-4", createdAt, null, null);
        GithubReview later = review(createdAt.plusSeconds(3600 * 10), ReviewResult.APPROVED);
        GithubReview earlier = review(createdAt.plusSeconds(3600 * 3), ReviewResult.CHANGES_REQUESTED);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoId("org-1", "repo-1")).thenReturn(List.of(pr));
        when(githubReviewRepository.findAllByOrganizationIdAndPullRequestId("org-1", "pr-4"))
                .thenReturn(List.of(later, earlier));

        useCase.execute("org-1", "repo-1");

        verify(githubPullRequestRepository).updateMetrics(eq("pr-4"), any(), eq(3.0));
    }

    @Test
    void shouldLeaveTimeToFirstReviewNull_whenNoReviews() {
        Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
        GithubPullRequest pr = prWith("pr-5", createdAt, null, null);
        when(githubPullRequestRepository.findAllByOrganizationIdAndRepoId("org-1", "repo-1")).thenReturn(List.of(pr));
        when(githubReviewRepository.findAllByOrganizationIdAndPullRequestId("org-1", "pr-5")).thenReturn(List.of());

        useCase.execute("org-1", "repo-1");

        ArgumentCaptor<Double> ttfrCaptor = ArgumentCaptor.forClass(Double.class);
        verify(githubPullRequestRepository).updateMetrics(eq("pr-5"), any(), ttfrCaptor.capture());
        assertThat(ttfrCaptor.getValue()).isNull();
    }

    private GithubPullRequest prWith(String id, Instant createdAt, Instant mergedAt, Instant closedAt) {
        return new GithubPullRequest(id, "org-1", "repo-1", "ext-" + id, 1, "title",
                PullRequestStatus.OPEN, "octocat", null, createdAt, mergedAt, closedAt, createdAt,
                null, null, true, Instant.now(), null);
    }

    private GithubReview review(Instant submittedAt, ReviewResult result) {
        return new GithubReview(null, "org-1", "pr-4", "rev-1", "reviewer", null, result, submittedAt,
                true, Instant.now(), Instant.now(), null);
    }
}
