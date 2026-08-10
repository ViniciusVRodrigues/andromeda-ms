package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.infrastructure.client.GhPullRequestDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncGithubPullRequestsUseCaseTest {

    @Mock private GithubPullRequestRepository githubPullRequestRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SyncGithubPullRequestsUseCase useCase;

    private GithubConnection connection;
    private GithubRepo repo;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        connection = new GithubConnection("conn-1", "org-1", "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, now, now, null);
        repo = new GithubRepo("repo-1", "org-1", "999", "octo", "hello", "octo/hello", now, true, now, now, null);

        lenient().when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        lenient().when(githubPullRequestRepository.findByOrganizationIdAndRepoIdAndExternalId(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(githubPullRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.findByOrganizationIdAndGithubId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void shouldUpsertUsingOrganizationRepoExternalIdKey() {
        GhPullRequestDto dto = new GhPullRequestDto(123L, 7, "Title", "open",
                new GhPullRequestDto.UserDto("octocat"),
                "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", null, null);
        when(githubApiClient.getPullRequests("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));

        useCase.execute(connection, repo, null);

        verify(githubPullRequestRepository).findByOrganizationIdAndRepoIdAndExternalId("org-1", "repo-1", "123");
    }

    @Test
    void shouldDeriveStatusMerged_whenMergedAtPresent() {
        GhPullRequestDto dto = new GhPullRequestDto(1L, 1, "T", "closed",
                new GhPullRequestDto.UserDto("octocat"),
                "2024-01-01T00:00:00Z", "2024-01-03T00:00:00Z", "2024-01-02T00:00:00Z", "2024-01-02T00:00:00Z");
        when(githubApiClient.getPullRequests("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubPullRequest> captor = ArgumentCaptor.forClass(GithubPullRequest.class);
        verify(githubPullRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PullRequestStatus.MERGED);
    }

    @Test
    void shouldDeriveStatusClosed_whenClosedWithoutMerge() {
        GhPullRequestDto dto = new GhPullRequestDto(2L, 2, "T", "closed",
                new GhPullRequestDto.UserDto("octocat"),
                "2024-01-01T00:00:00Z", "2024-01-03T00:00:00Z", null, "2024-01-02T00:00:00Z");
        when(githubApiClient.getPullRequests("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubPullRequest> captor = ArgumentCaptor.forClass(GithubPullRequest.class);
        verify(githubPullRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PullRequestStatus.CLOSED);
    }

    @Test
    void shouldDeriveStatusOpen_whenNeitherMergedNorClosed() {
        GhPullRequestDto dto = new GhPullRequestDto(3L, 3, "T", "open",
                new GhPullRequestDto.UserDto("octocat"),
                "2024-01-01T00:00:00Z", "2024-01-03T00:00:00Z", null, null);
        when(githubApiClient.getPullRequests("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubPullRequest> captor = ArgumentCaptor.forClass(GithubPullRequest.class);
        verify(githubPullRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PullRequestStatus.OPEN);
    }
}
