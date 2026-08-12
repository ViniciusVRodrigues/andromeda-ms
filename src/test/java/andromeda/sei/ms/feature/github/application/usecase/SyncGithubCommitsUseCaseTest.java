package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubCommit;
import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.infrastructure.client.GhCommitDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncGithubCommitsUseCaseTest {

    @Mock private GithubCommitRepository githubCommitRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private EncryptionService encryptionService;
    @Mock private GithubAuthorResolver githubAuthorResolver;

    @InjectMocks
    private SyncGithubCommitsUseCase useCase;

    private GithubConnection connection;
    private GithubRepo repo;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        connection = new GithubConnection("conn-1", "org-1", "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, now, now, null);
        repo = new GithubRepo("repo-1", "org-1", "999", "octo", "hello", "octo/hello", now, true, now, now, null);

        lenient().when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        lenient().when(githubCommitRepository.findByOrganizationIdAndRepoIdAndSha(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(githubCommitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldResolveAuthorUserId_whenNumericGithubIdMatchesLinkedUser() {
        // BUG 2 fix: resolution matches User.githubId against the NUMERIC author.id returned by
        // the GitHub API (the value GitHubOAuth2SuccessHandler stores), not the login.
        GhCommitDto dto = new GhCommitDto("sha1",
                new GhCommitDto.CommitDetailDto("msg", new GhCommitDto.GitAuthorDto("A", "a@x.com", "2024-01-01T00:00:00Z")),
                new GhCommitDto.AuthorDto("octocat", 58421L));
        when(githubApiClient.getCommits("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));
        when(githubAuthorResolver.resolve("org-1", 58421L)).thenReturn("user-42");

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubCommit> captor = ArgumentCaptor.forClass(GithubCommit.class);
        verify(githubCommitRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorUserId()).isEqualTo("user-42");
        assertThat(captor.getValue().getAuthorGithubLogin()).isEqualTo("octocat");
    }

    @Test
    void shouldLeaveAuthorUserIdNull_whenNumericGithubIdHasNoLinkedUser_butKeepLogin() {
        GhCommitDto dto = new GhCommitDto("sha2",
                new GhCommitDto.CommitDetailDto("msg", new GhCommitDto.GitAuthorDto("B", "b@x.com", "2024-01-02T00:00:00Z")),
                new GhCommitDto.AuthorDto("unlinked-user", 99999L));
        when(githubApiClient.getCommits("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));
        when(githubAuthorResolver.resolve("org-1", 99999L)).thenReturn(null);

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubCommit> captor = ArgumentCaptor.forClass(GithubCommit.class);
        verify(githubCommitRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorUserId()).isNull();
        assertThat(captor.getValue().getAuthorGithubLogin()).isEqualTo("unlinked-user");
    }

    @Test
    void shouldLeaveAuthorUserIdNull_whenTopLevelAuthorIsMissing() {
        // Commits authored outside GitHub have no top-level author object at all.
        GhCommitDto dto = new GhCommitDto("sha3",
                new GhCommitDto.CommitDetailDto("msg", new GhCommitDto.GitAuthorDto("C", "c@x.com", "2024-01-03T00:00:00Z")),
                null);
        when(githubApiClient.getCommits("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubCommit> captor = ArgumentCaptor.forClass(GithubCommit.class);
        verify(githubCommitRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorUserId()).isNull();
        assertThat(captor.getValue().getAuthorGithubLogin()).isNull();
        verify(githubAuthorResolver).resolve("org-1", null);
    }

    @Test
    void shouldPassLastSyncAtAsSinceWatermark() {
        Instant watermark = Instant.parse("2024-05-01T00:00:00Z");
        when(githubApiClient.getCommits(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq(watermark)))
                .thenReturn(List.of());

        useCase.execute(connection, repo, watermark);

        verify(githubApiClient).getCommits("octo", "hello", "Bearer plain-pat", watermark);
    }
}
