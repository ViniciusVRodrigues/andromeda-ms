package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.PullRequestCommitsUnavailableException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** vcs-repository-read-api: pull request commits fetched live through the provider port. */
@ExtendWith(MockitoExtension.class)
class GetPullRequestCommitsUseCaseTest {

    private static final String ORG = "org-1";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private VcsReadPort vcsReadPort;
    @Mock private VcsPullRequestRepository pullRequestRepository;
    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsCredentialsProvider credentialsProvider;
    @Mock private VcsProviderClient client;

    @InjectMocks
    private GetPullRequestCommitsUseCase useCase;

    private final VcsRepository repo = new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello");
    private final VcsPullRequest pr = new VcsPullRequest("pr-1", ORG, "repo-1", VcsProvider.GITHUB, "9", 9, "t",
            PullRequestStatus.OPEN);

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        lenient().when(vcsReadPort.repository(ORG, "repo-1")).thenReturn(Optional.of(repo));
        lenient().when(pullRequestRepository.findByIdAndOrganizationId("pr-1", ORG)).thenReturn(Optional.of(pr));
        lenient().when(providerRegistry.credentialsProvider(VcsProvider.GITHUB)).thenReturn(credentialsProvider);
        lenient().when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(CREDENTIALS));
        lenient().when(providerRegistry.client(VcsProvider.GITHUB)).thenReturn(client);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_returnCommitsFromTheProvider() {
        when(client.pullRequestCommits(CREDENTIALS, repo, pr)).thenReturn(List.of(
                new VcsRemoteCommit("abc", "msg", "octocat", "1", Instant.parse("2026-08-01T00:00:00Z"))));

        List<PullRequestCommitOutput> commits = useCase.execute("repo-1", "pr-1");

        assertThat(commits).singleElement().satisfies(c -> {
            assertThat(c.sha()).isEqualTo("abc");
            assertThat(c.authorLogin()).isEqualTo("octocat");
        });
    }

    @Test
    void should_answerNotFound_when_pullRequestBelongsToAnotherRepository() {
        pr.setRepositoryId("repo-2");

        assertThatThrownBy(() -> useCase.execute("repo-1", "pr-1")).isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void should_answerNotFound_when_repositoryOfAnotherOrganization() {
        when(vcsReadPort.repository(ORG, "repo-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("repo-x", "pr-1")).isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void should_answerConflict_when_providerHasNoConnection() {
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("repo-1", "pr-1")).isInstanceOf(VcsConnectionNotFoundException.class);
    }

    @Test
    void should_wrapProviderFailure_asCommitsUnavailable() {
        when(client.pullRequestCommits(CREDENTIALS, repo, pr)).thenThrow(new IllegalStateException("401"));

        assertThatThrownBy(() -> useCase.execute("repo-1", "pr-1"))
                .isInstanceOf(PullRequestCommitsUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "PR_COMMITS_UNAVAILABLE");
    }
}
