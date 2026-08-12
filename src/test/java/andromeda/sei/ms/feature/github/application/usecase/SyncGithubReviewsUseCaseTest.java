package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubReview;
import andromeda.sei.ms.feature.github.domain.GithubReviewRepository;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.infrastructure.client.GhReviewDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
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

    @Test
    void shouldResolveReviewerUserId_whenNumericGithubIdMatchesLinkedUser() {
        // BUG 2 fix: resolution matches User.githubId against the NUMERIC user.id returned by
        // the GitHub API, while the login keeps being persisted for display/fallback.
        when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        lenient().when(githubReviewRepository.findByOrganizationIdAndPullRequestIdAndExternalId(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(githubReviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GithubPullRequest pr = pullRequest("pr-1", 5, Instant.parse("2024-01-01T00:00:00Z"));
        GhReviewDto dto = new GhReviewDto(77L, new GhReviewDto.UserDto("octocat", 58421L),
                "APPROVED", "2024-01-02T00:00:00Z");
        when(githubApiClient.getReviews("octo", "hello", 5, "Bearer plain-pat")).thenReturn(List.of(dto));
        when(userRepository.findByOrganizationIdAndGithubId("org-1", "58421"))
                .thenReturn(Optional.of(userWithId("user-42")));

        useCase.execute(connection, repo, List.of(pr), null);

        ArgumentCaptor<GithubReview> captor = ArgumentCaptor.forClass(GithubReview.class);
        verify(githubReviewRepository).save(captor.capture());
        assertThat(captor.getValue().getReviewerUserId()).isEqualTo("user-42");
        assertThat(captor.getValue().getReviewerGithubLogin()).isEqualTo("octocat");
    }

    private GithubPullRequest pullRequest(String id, int number, Instant externalUpdatedAt) {
        return new GithubPullRequest(id, "org-1", "repo-1", "ext-" + id, number, "title",
                PullRequestStatus.OPEN, "octocat", null, Instant.now(), null, null, externalUpdatedAt,
                null, null, true, Instant.now(), null);
    }

    private User userWithId(String id) {
        return new User(id, "org-1", "e@x.com", null, "Name", "role-1", null, "58421",
                null, null, null, true, true, 0L, Instant.now(), Instant.now());
    }
}
