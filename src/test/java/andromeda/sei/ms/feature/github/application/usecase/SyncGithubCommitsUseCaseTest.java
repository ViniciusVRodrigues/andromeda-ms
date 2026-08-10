package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubCommit;
import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.infrastructure.client.GhCommitDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncGithubCommitsUseCaseTest {

    @Mock private GithubCommitRepository githubCommitRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;

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
    void shouldResolveAuthorUserId_whenGithubLoginMatchesLinkedUser() {
        GhCommitDto dto = new GhCommitDto("sha1",
                new GhCommitDto.CommitDetailDto("msg", new GhCommitDto.GitAuthorDto("A", "a@x.com", "2024-01-01T00:00:00Z")),
                new GhCommitDto.AuthorDto("octocat"));
        when(githubApiClient.getCommits("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));
        when(userRepository.findByOrganizationIdAndGithubId("org-1", "octocat"))
                .thenReturn(Optional.of(userWithId("user-42")));

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubCommit> captor = ArgumentCaptor.forClass(GithubCommit.class);
        verify(githubCommitRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorUserId()).isEqualTo("user-42");
        assertThat(captor.getValue().getAuthorGithubLogin()).isEqualTo("octocat");
    }

    @Test
    void shouldLeaveAuthorUserIdNull_whenGithubLoginHasNoLinkedUser() {
        GhCommitDto dto = new GhCommitDto("sha2",
                new GhCommitDto.CommitDetailDto("msg", new GhCommitDto.GitAuthorDto("B", "b@x.com", "2024-01-02T00:00:00Z")),
                new GhCommitDto.AuthorDto("unlinked-user"));
        when(githubApiClient.getCommits("octo", "hello", "Bearer plain-pat", null)).thenReturn(List.of(dto));
        when(userRepository.findByOrganizationIdAndGithubId("org-1", "unlinked-user")).thenReturn(Optional.empty());

        useCase.execute(connection, repo, null);

        ArgumentCaptor<GithubCommit> captor = ArgumentCaptor.forClass(GithubCommit.class);
        verify(githubCommitRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorUserId()).isNull();
        assertThat(captor.getValue().getAuthorGithubLogin()).isEqualTo("unlinked-user");
    }

    @Test
    void shouldPassLastSyncAtAsSinceWatermark() {
        Instant watermark = Instant.parse("2024-05-01T00:00:00Z");
        when(githubApiClient.getCommits(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq(watermark)))
                .thenReturn(List.of());

        useCase.execute(connection, repo, watermark);

        verify(githubApiClient).getCommits("octo", "hello", "Bearer plain-pat", watermark);
    }

    private User userWithId(String id) {
        User user = new User(id, "org-1", "e@x.com", null, "Name", "role-1", null, "octocat",
                null, null, null, true, true, 0L, Instant.now(), Instant.now());
        return user;
    }
}
