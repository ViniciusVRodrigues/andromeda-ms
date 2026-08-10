package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubReviewRepository;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncGithubReviewsUseCaseTest {

    @Mock private GithubReviewRepository githubReviewRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SyncGithubReviewsUseCase useCase;

    private GithubConnection connection;
    private GithubRepo repo;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        connection = new GithubConnection("conn-1", "org-1", "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, now, now, null);
        repo = new GithubRepo("repo-1", "org-1", "999", "octo", "hello", "octo/hello", now, true, now, now, null);
    }

    @Test
    void shouldOnlyFetchReviews_forPullRequestsUpdatedSinceWatermark() {
        when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        Instant watermark = Instant.parse("2024-06-01T00:00:00Z");

        GithubPullRequest staleUpdate = pullRequest("pr-1", 10, Instant.parse("2024-05-01T00:00:00Z"));
        GithubPullRequest freshUpdate = pullRequest("pr-2", 20, Instant.parse("2024-06-15T00:00:00Z"));

        when(githubApiClient.getReviews("octo", "hello", 20, "Bearer plain-pat")).thenReturn(List.of());

        useCase.execute(connection, repo, List.of(staleUpdate, freshUpdate), watermark);

        verify(githubApiClient, never()).getReviews("octo", "hello", 10, "Bearer plain-pat");
        verify(githubApiClient, times(1)).getReviews("octo", "hello", 20, "Bearer plain-pat");
    }

    @Test
    void shouldFetchAllReviews_whenNoWatermarkYet() {
        when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        GithubPullRequest pr = pullRequest("pr-1", 5, Instant.parse("2024-01-01T00:00:00Z"));
        when(githubApiClient.getReviews("octo", "hello", 5, "Bearer plain-pat")).thenReturn(List.of());

        useCase.execute(connection, repo, List.of(pr), null);

        verify(githubApiClient).getReviews("octo", "hello", 5, "Bearer plain-pat");
    }

    private GithubPullRequest pullRequest(String id, int number, Instant externalUpdatedAt) {
        return new GithubPullRequest(id, "org-1", "repo-1", "ext-" + id, number, "title",
                PullRequestStatus.OPEN, "octocat", null, Instant.now(), null, null, externalUpdatedAt,
                null, null, true, Instant.now(), null);
    }
}
