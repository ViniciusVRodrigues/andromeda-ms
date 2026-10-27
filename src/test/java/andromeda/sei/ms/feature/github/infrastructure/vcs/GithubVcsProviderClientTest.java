package andromeda.sei.ms.feature.github.infrastructure.vcs;

import andromeda.sei.ms.feature.github.infrastructure.client.GhBranchDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GhCommitDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GhPullRequestDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GhRepoDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GhReviewDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoIdInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsBranchNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The GitHub adapter of the provider ports: payload → neutral records (vcs-core task 3.5). */
@ExtendWith(MockitoExtension.class)
class GithubVcsProviderClientTest {

    private static final String AUTH = "Bearer plain-pat";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, "org-1", "plain-pat", false);

    @Mock private GithubApiClient githubApiClient;

    @InjectMocks
    private GithubVcsProviderClient client;

    private final VcsRepository repo = repository("repo-1", "123", "octo/hello");

    // ---- pull request status and fields

    @Test
    void should_deriveMerged_when_mergedAtPresent() {
        givenPullRequests(pr(1L, "closed", "2024-01-02T00:00:00Z"));

        assertThat(pullRequests().get(0).status()).isEqualTo(PullRequestStatus.MERGED);
    }

    @Test
    void should_deriveClosed_when_closedWithoutMerge() {
        givenPullRequests(pr(2L, "closed", null));

        assertThat(pullRequests().get(0).status()).isEqualTo(PullRequestStatus.CLOSED);
    }

    @Test
    void should_deriveOpen_when_neitherMergedNorClosed() {
        givenPullRequests(pr(3L, "open", null));

        assertThat(pullRequests().get(0).status()).isEqualTo(PullRequestStatus.OPEN);
    }

    @Test
    void should_mapDraftBranchesLabelsAndAuthor_when_payloadCarriesThem() {
        GhPullRequestDto dto = new GhPullRequestDto(40L, 40, "Add export", "open",
                new GhPullRequestDto.UserDto("octocat", 58421L),
                "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", null, null, "Closes AB#7",
                new GhPullRequestDto.HeadDto("feature/export"), new GhPullRequestDto.HeadDto("main"), true,
                List.of(new GhPullRequestDto.LabelDto("bug"), new GhPullRequestDto.LabelDto("ui")));
        givenPullRequests(dto);

        VcsRemotePullRequest remote = pullRequests().get(0);

        assertThat(remote.externalId()).isEqualTo("40");
        assertThat(remote.number()).isEqualTo(40);
        assertThat(remote.draft()).isTrue();
        assertThat(remote.sourceBranch()).isEqualTo("feature/export");
        assertThat(remote.baseBranch()).isEqualTo("main");
        assertThat(remote.labels()).containsExactly("bug", "ui");
        assertThat(remote.authorLogin()).isEqualTo("octocat");
        assertThat(remote.authorExternalId()).isEqualTo("58421");
        assertThat(remote.body()).isEqualTo("Closes AB#7");
        assertThat(remote.createdAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void should_readOnlyPullRequestsCreatedInBackfillWindow_when_firstSync() {
        Instant backfillFrom = Instant.parse("2026-07-02T00:00:00Z");
        when(githubApiClient.getPullRequestsCreatedSince("octo", "hello", AUTH, backfillFrom)).thenReturn(List.of());

        client.pullRequestsChangedSince(CREDENTIALS, repo, null, backfillFrom);

        verify(githubApiClient, never()).getPullRequests(any(), any(), any(), any());
    }

    @Test
    void should_ignoreBackfillWindow_when_incrementalSync() {
        Instant watermark = Instant.parse("2026-09-29T00:00:00Z");
        when(githubApiClient.getPullRequests("octo", "hello", AUTH, watermark)).thenReturn(List.of());

        client.pullRequestsChangedSince(CREDENTIALS, repo, watermark, Instant.parse("2026-07-02T00:00:00Z"));

        verify(githubApiClient, never()).getPullRequestsCreatedSince(any(), any(), any(), any());
    }

    // ---- commits

    @Test
    void should_keepGitDateAndLeaveAuthorNull_when_commitHasNoGithubAccount() {
        GhCommitDto dto = new GhCommitDto("abc", new GhCommitDto.CommitDetailDto("msg",
                new GhCommitDto.GitAuthorDto("A", "a@x", "2024-01-05T10:00:00Z")), null);
        Instant since = Instant.parse("2024-01-01T00:00:00Z");
        when(githubApiClient.getCommits("octo", "hello", AUTH, "main", since)).thenReturn(List.of(dto));

        VcsRemoteCommit commit = client.commitsSince(CREDENTIALS, repo, "main", since).get(0);

        assertThat(commit.sha()).isEqualTo("abc");
        assertThat(commit.authorLogin()).isNull();
        assertThat(commit.authorExternalId()).isNull();
        assertThat(commit.authoredAt()).isEqualTo(Instant.parse("2024-01-05T10:00:00Z"));
    }

    @Test
    void should_mapCommitAuthor_when_githubAccountPresent() {
        GhCommitDto dto = new GhCommitDto("def", new GhCommitDto.CommitDetailDto("msg", null),
                new GhCommitDto.AuthorDto("octocat", 58421L));
        when(githubApiClient.getCommits("octo", "hello", AUTH, "release/1", null)).thenReturn(List.of(dto));

        VcsRemoteCommit commit = client.commitsSince(CREDENTIALS, repo, "release/1", null).get(0);

        assertThat(commit.authorLogin()).isEqualTo("octocat");
        assertThat(commit.authorExternalId()).isEqualTo("58421");
        assertThat(commit.authoredAt()).isNull();
    }

    @Test
    void should_raiseBranchNotFound_when_githubAnswers404Or422() {
        for (int status : new int[]{404, 422}) {
            org.mockito.Mockito.doThrow(
                    WebClientResponseException.create(status, "x", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))
                    .when(githubApiClient).getCommits("octo", "hello", AUTH, "gone", null);

            assertThatThrownBy(() -> client.commitsSince(CREDENTIALS, repo, "gone", null))
                    .isInstanceOf(VcsBranchNotFoundException.class);
        }
    }

    @Test
    void should_notSwallowOtherProviderErrors_when_readingBranchCommits() {
        when(githubApiClient.getCommits("octo", "hello", AUTH, "main", null)).thenThrow(
                WebClientResponseException.create(500, "x", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.commitsSince(CREDENTIALS, repo, "main", null))
                .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    void should_listBranchesWithDefaultFirst() {
        repo.setDefaultBranch("develop");
        when(githubApiClient.getBranches("octo", "hello", AUTH, VcsProviderClient.MAX_BRANCHES_LISTED + 1))
                .thenReturn(List.of(new GhBranchDto("feature/x"), new GhBranchDto("develop"), new GhBranchDto("main")));

        List<VcsBranch> branches = client.listBranches(CREDENTIALS, repo);

        assertThat(branches).extracting(VcsBranch::name).containsExactly("develop", "feature/x", "main");
        assertThat(branches).extracting(VcsBranch::isDefault).containsExactly(true, false, false);
    }

    // ---- review signals (spec: APPROVED/CHANGES_REQUESTED/COMMENTED 1:1, DISMISSED flagged, PENDING dropped)

    @Test
    void should_mapHumanApproval_toApprovedEvent() {
        givenReviews(review(1L, "alice", null, "APPROVED", "2024-01-01T05:00:00Z"));

        VcsReviewSignal signal = signals().get(0);

        assertThat(signal.kind()).isEqualTo(ReviewKind.APPROVED);
        assertThat(signal.bot()).isFalse();
        assertThat(signal.dismissed()).isFalse();
        assertThat(signal.occurredAt()).isEqualTo(Instant.parse("2024-01-01T05:00:00Z"));
        assertThat(signal.externalEventId()).isEqualTo("1");
        assertThat(signal.actorExternalId()).isEqualTo("1001");
    }

    @Test
    void should_flagBots_byTypeOrLoginSuffix() {
        givenReviews(review(2L, "ci-helper", "Bot", "COMMENTED", "2024-01-01T05:00:00Z"),
                review(3L, "dependabot[bot]", "User", "COMMENTED", "2024-01-01T05:00:00Z"),
                review(4L, "carol", "User", "CHANGES_REQUESTED", "2024-01-01T05:00:00Z"));

        assertThat(signals()).extracting(VcsReviewSignal::bot).containsExactly(true, true, false);
        assertThat(signals()).extracting(VcsReviewSignal::kind)
                .containsExactly(ReviewKind.COMMENTED, ReviewKind.COMMENTED, ReviewKind.CHANGES_REQUESTED);
    }

    @Test
    void should_flagDismissed_withoutKind() {
        givenReviews(review(5L, "alice", null, "DISMISSED", "2024-01-01T05:00:00Z"));

        VcsReviewSignal signal = signals().get(0);

        assertThat(signal.dismissed()).isTrue();
        assertThat(signal.kind()).isNull();
    }

    @Test
    void should_dropPendingAndUnknownStates() {
        givenReviews(review(6L, "alice", null, "PENDING", null), review(7L, "bob", null, "SOMETHING", null));

        assertThat(signals()).isEmpty();
    }

    // ---- repositories

    @Test
    void should_fetchById_when_externalIdKnown() {
        when(githubApiClient.getRepoById("123", AUTH)).thenReturn(
                new GhRepoDto(123L, "hello-renamed", "octo/hello-renamed", null, "2024-02-01T00:00:00Z", "trunk"));

        VcsRemoteRepository remote = client.getRepository(CREDENTIALS, repo);

        assertThat(remote.externalRepoId()).isEqualTo("123");
        assertThat(remote.displayName()).isEqualTo("octo/hello-renamed");
        assertThat(remote.defaultBranch()).isEqualTo("trunk");
        verify(githubApiClient, never()).getRepo(any(), any(), any());
    }

    @Test
    void should_resolveByOwnerAndName_when_repositoryIsPending() {
        when(githubApiClient.getRepo("octo", "new", AUTH)).thenReturn(
                new GhRepoDto(456L, "new", "octo/new", null, null, "main"));

        VcsRemoteRepository remote = client.getRepository(CREDENTIALS, repository("repo-2", null, "octo/new"));

        assertThat(remote.externalRepoId()).isEqualTo("456");
    }

    @Test
    void should_rejectMalformedName_withRepoIdInvalid() {
        assertThatThrownBy(() -> client.resolveRepository(CREDENTIALS, "not-a-full-name"))
                .isInstanceOf(RepoIdInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "REPO_ID_INVALID");
        verifyNoInteractions(githubApiClient);
    }

    @Test
    void should_listBindableRepositories_withNumericIdAndFullName() {
        when(githubApiClient.getUserRepos(AUTH, GithubVcsProviderClient.MAX_BINDABLE_PAGES)).thenReturn(List.of(
                new GhRepoDto(9L, "hello", "octo/hello", null, null, "main"),
                new GhRepoDto(10L, "nameless", null, null, null, null)));

        assertThat(client.listBindableRepositories(CREDENTIALS))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.externalRepoId()).isEqualTo("9");
                    assertThat(r.displayName()).isEqualTo("octo/hello");
                    assertThat(r.defaultBranch()).isEqualTo("main");
                });
    }

    @Test
    void should_haveNoNativeWorkItemLinks() {
        assertThat(client.nativeWorkItemLinks(CREDENTIALS, repo, null)).isEmpty();
        verifyNoInteractions(githubApiClient);
    }

    @Test
    void should_fetchPullRequestCommitsByNumber() {
        VcsPullRequest pr = new VcsPullRequest("pr-1", "org-1", "repo-1", VcsProvider.GITHUB, "77", 7, "t",
                PullRequestStatus.OPEN);
        when(githubApiClient.getPullRequestCommits("octo", "hello", 7, AUTH)).thenReturn(List.of());

        assertThat(client.pullRequestCommits(CREDENTIALS, repo, pr)).isEmpty();
    }

    private List<VcsRemotePullRequest> pullRequests() {
        return client.pullRequestsChangedSince(CREDENTIALS, repo, null, null);
    }

    private List<VcsReviewSignal> signals() {
        VcsPullRequest pr = new VcsPullRequest("pr-1", "org-1", "repo-1", VcsProvider.GITHUB, "77", 7, "t",
                PullRequestStatus.OPEN);
        return client.reviewSignals(CREDENTIALS, repo, pr);
    }

    private void givenPullRequests(GhPullRequestDto... dtos) {
        when(githubApiClient.getPullRequests("octo", "hello", AUTH, null)).thenReturn(List.of(dtos));
    }

    private void givenReviews(GhReviewDto... dtos) {
        when(githubApiClient.getReviews("octo", "hello", 7, AUTH)).thenReturn(List.of(dtos));
    }

    private static GhPullRequestDto pr(long id, String state, String mergedAt) {
        return new GhPullRequestDto(id, (int) id, "T", state, new GhPullRequestDto.UserDto("octocat", 58421L),
                "2024-01-01T00:00:00Z", "2024-01-03T00:00:00Z", mergedAt,
                "closed".equals(state) ? "2024-01-02T00:00:00Z" : null, null, null);
    }

    private static GhReviewDto review(long id, String login, String type, String state, String submittedAt) {
        return new GhReviewDto(id, new GhReviewDto.UserDto(login, 1001L, type), state, submittedAt);
    }

    private static VcsRepository repository(String id, String externalId, String displayName) {
        return new VcsRepository(id, "org-1", VcsProvider.GITHUB, externalId, displayName);
    }
}
