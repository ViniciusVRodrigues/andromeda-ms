package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Content steps of the neutral sync — the scenarios of the former {@code SyncGithubCommits},
 * {@code SyncGithubPullRequests} and {@code SyncGithubReviews} tests, now through the ports.
 */
@ExtendWith(MockitoExtension.class)
class VcsRepositoryContentSyncTest {

    private static final String ORG = "org-1";
    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private VcsCommitRepository commitRepository;
    @Mock private VcsPullRequestRepository pullRequestRepository;
    @Mock private VcsReviewEventRepository reviewEventRepository;
    @Mock private VcsProviderClient client;
    @Mock private VcsAuthorResolver authors;

    @InjectMocks
    private VcsRepositoryContentSync contentSync;

    private final VcsRepository repository = new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello");
    private VcsRepositoryContentSync.Context context;

    @BeforeEach
    void setUp() {
        context = new VcsRepositoryContentSync.Context(CREDENTIALS, client, authors, repository, WorkItemLinkConfig.defaults());
        lenient().when(commitRepository.findByOrganizationIdAndRepositoryIdAndSha(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(pullRequestRepository.findByOrganizationIdAndRepositoryIdAndExternalId(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(pullRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(reviewEventRepository.findByOrganizationIdAndPullRequestIdAndExternalEventId(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(client.nativeWorkItemLinks(any(), any(), any())).thenReturn(List.of());
    }

    // ---- commits

    @Test
    void should_resolveCommitAuthorUserId_andKeepLogin() {
        when(client.commitsSince(CREDENTIALS, repository, T0)).thenReturn(List.of(
                new VcsRemoteCommit("sha-1", "msg", "octocat", "58421", T0)));
        when(authors.resolveUserId(ORG, "58421")).thenReturn("user-42");

        int processed = contentSync.syncCommits(context, T0);

        VcsCommit saved = capture(VcsCommit.class, commitRepository);
        assertThat(processed).isEqualTo(1);
        assertThat(saved.getAuthorUserId()).isEqualTo("user-42");
        assertThat(saved.getAuthorLogin()).isEqualTo("octocat");
        assertThat(saved.getRepositoryId()).isEqualTo("repo-1");
        assertThat(saved.getProvider()).isEqualTo(VcsProvider.GITHUB);
    }

    @Test
    void should_leaveCommitAuthorUserIdNull_when_actorUnlinked() {
        when(client.commitsSince(CREDENTIALS, repository, null)).thenReturn(List.of(
                new VcsRemoteCommit("sha-2", "msg", "stranger", "999", T0)));

        contentSync.syncCommits(context, null);

        VcsCommit saved = capture(VcsCommit.class, commitRepository);
        assertThat(saved.getAuthorUserId()).isNull();
        assertThat(saved.getAuthorLogin()).isEqualTo("stranger");
    }

    @Test
    void should_skipOnlyTheFailingCommit() {
        when(client.commitsSince(CREDENTIALS, repository, null)).thenReturn(List.of(
                new VcsRemoteCommit("bad", "m", null, null, T0), new VcsRemoteCommit("good", "m", null, null, T0)));
        when(commitRepository.findByOrganizationIdAndRepositoryIdAndSha(ORG, "repo-1", "bad"))
                .thenThrow(new IllegalStateException("corrupt"));

        assertThat(contentSync.syncCommits(context, null)).isEqualTo(1);
    }

    // ---- pull requests

    @Test
    void should_upsertPullRequest_withNeutralAuthorAndProvider() {
        givenRemotePullRequests(remote("40", 40, PullRequestStatus.OPEN, "feature/export", null, "Add export"));
        when(authors.resolveUserId(ORG, "58421")).thenReturn("user-42");

        List<VcsPullRequest> synced = contentSync.syncPullRequests(context, null, null);

        VcsPullRequest saved = synced.get(0);
        assertThat(saved.getExternalId()).isEqualTo("40");
        assertThat(saved.getRepositoryId()).isEqualTo("repo-1");
        assertThat(saved.getProvider()).isEqualTo(VcsProvider.GITHUB);
        assertThat(saved.getAuthorLogin()).isEqualTo("octocat");
        assertThat(saved.getAuthorExternalId()).isEqualTo("58421");
        assertThat(saved.getAuthorUserId()).isEqualTo("user-42");
        assertThat(saved.getSourceBranch()).isEqualTo("feature/export");
        assertThat(saved.getBaseBranch()).isEqualTo("main");
        assertThat(saved.isDraft()).isTrue();
        assertThat(saved.getLabels()).containsExactly("ui");
        verify(client, never()).pullRequestCommits(any(), any(), any());
    }

    @Test
    void should_storeEarliestCommitAuthoredAt_when_pullRequestIsMerged() {
        givenRemotePullRequests(remote("41", 41, PullRequestStatus.MERGED, "hotfix/x", null, "T"));
        when(client.pullRequestCommits(eq(CREDENTIALS), eq(repository), any())).thenReturn(List.of(
                new VcsRemoteCommit("a", "m", null, null, Instant.parse("2023-12-30T10:00:00Z")),
                new VcsRemoteCommit("b", "m", null, null, Instant.parse("2023-12-29T08:00:00Z")),
                new VcsRemoteCommit("c", "m", null, null, null)));

        VcsPullRequest saved = contentSync.syncPullRequests(context, null, null).get(0);

        assertThat(saved.getEarliestCommitAt()).isEqualTo(Instant.parse("2023-12-29T08:00:00Z"));
    }

    @Test
    void should_notRefetchCommits_when_earliestCommitAlreadyKnown() {
        VcsPullRequest existing = new VcsPullRequest("pr-42", ORG, "repo-1", VcsProvider.GITHUB, "42", 42, "T",
                PullRequestStatus.MERGED);
        existing.setCreatedAt(T0);
        existing.setEarliestCommitAt(Instant.parse("2023-12-01T00:00:00Z"));
        when(pullRequestRepository.findByOrganizationIdAndRepositoryIdAndExternalId(ORG, "repo-1", "42"))
                .thenReturn(Optional.of(existing));
        givenRemotePullRequests(remote("42", 42, PullRequestStatus.MERGED, "hotfix/x", null, "T"));

        VcsPullRequest saved = contentSync.syncPullRequests(context, null, null).get(0);

        verify(client, never()).pullRequestCommits(any(), any(), any());
        assertThat(saved.getEarliestCommitAt()).isEqualTo(Instant.parse("2023-12-01T00:00:00Z"));
    }

    @Test
    void should_keepSyncing_when_pullRequestCommitsCannotBeRead() {
        givenRemotePullRequests(remote("43", 43, PullRequestStatus.MERGED, "x", null, "T"));
        when(client.pullRequestCommits(any(), any(), any())).thenThrow(new RuntimeException("403"));

        List<VcsPullRequest> synced = contentSync.syncPullRequests(context, null, null);

        assertThat(synced).hasSize(1);
        assertThat(synced.get(0).getEarliestCommitAt()).isNull();
    }

    @Test
    void should_skipOnlyTheFailingPullRequest_when_oneUpsertThrows() {
        givenRemotePullRequests(remote("50", 50, PullRequestStatus.OPEN, null, null, "bad"),
                remote("51", 51, PullRequestStatus.OPEN, null, null, "good"));
        when(pullRequestRepository.findByOrganizationIdAndRepositoryIdAndExternalId(ORG, "repo-1", "50"))
                .thenThrow(new IllegalStateException("corrupt row"));

        List<VcsPullRequest> synced = contentSync.syncPullRequests(context, null, null);

        assertThat(synced).extracting(VcsPullRequest::getNumber).containsExactly(51);
    }

    @Test
    void should_passWatermarkAndBackfillToTheProvider() {
        Instant watermark = Instant.parse("2026-09-29T00:00:00Z");
        Instant backfill = Instant.parse("2026-07-02T00:00:00Z");
        when(client.pullRequestsChangedSince(CREDENTIALS, repository, watermark, backfill)).thenReturn(List.of());

        contentSync.syncPullRequests(context, watermark, backfill);

        verify(client).pullRequestsChangedSince(CREDENTIALS, repository, watermark, backfill);
    }

    // ---- work item link cascade (vcs-work-item-linking)

    @Test
    void should_preferNativeLink_overTextReference() {
        VcsRemotePullRequest pr = remote("60", 60, PullRequestStatus.OPEN, "feature/AB#7-x", null, "AB#7 title");
        givenRemotePullRequests(pr);
        when(client.nativeWorkItemLinks(CREDENTIALS, repository, pr)).thenReturn(List.of("42"));

        assertThat(contentSync.syncPullRequests(context, null, null).get(0).getLinkedWorkItemExternalId())
                .isEqualTo("42");
    }

    @Test
    void should_storeTextReference_evenWhenWorkItemNotSyncedYet() {
        givenRemotePullRequests(remote("61", 61, PullRequestStatus.OPEN, "feature/x", "Closes AB#99", "title"));

        assertThat(contentSync.syncPullRequests(context, null, null).get(0).getLinkedWorkItemExternalId())
                .isEqualTo("99");
    }

    @Test
    void should_notLink_when_linkingDisabled() {
        context = new VcsRepositoryContentSync.Context(CREDENTIALS, client, authors, repository,
                new WorkItemLinkConfig(false, List.of("AB#")));
        givenRemotePullRequests(remote("62", 62, PullRequestStatus.OPEN, "feature/AB#5", null, "t"));

        assertThat(contentSync.syncPullRequests(context, null, null).get(0).getLinkedWorkItemExternalId()).isNull();
    }

    @Test
    void should_notFailSync_when_nativeLinksThrow() {
        givenRemotePullRequests(remote("63", 63, PullRequestStatus.OPEN, "feature/AB#5", null, "t"));
        when(client.nativeWorkItemLinks(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        List<VcsPullRequest> synced = contentSync.syncPullRequests(context, null, null);

        assertThat(synced).hasSize(1);
        assertThat(synced.get(0).getLinkedWorkItemExternalId()).isNull();
    }

    // ---- review events

    @Test
    void should_fetchEventsOnlyForPullRequestsUpdatedSinceWatermark() {
        Instant watermark = Instant.parse("2024-01-10T00:00:00Z");
        VcsPullRequest stale = pr("pr-1", 1, watermark.minusSeconds(60));
        VcsPullRequest fresh = pr("pr-2", 2, watermark.plusSeconds(60));
        when(client.reviewSignals(CREDENTIALS, repository, fresh)).thenReturn(List.of());

        contentSync.syncReviewEvents(context, List.of(stale, fresh), watermark);

        verify(client, never()).reviewSignals(CREDENTIALS, repository, stale);
        verify(client).reviewSignals(CREDENTIALS, repository, fresh);
    }

    @Test
    void should_fetchEventsOfEveryPullRequest_when_noWatermark() {
        VcsPullRequest first = pr("pr-1", 1, null);
        VcsPullRequest second = pr("pr-2", 2, null);
        when(client.reviewSignals(any(), any(), any())).thenReturn(List.of());

        contentSync.syncReviewEvents(context, List.of(first, second), null);

        verify(client, times(2)).reviewSignals(any(), any(), any());
    }

    @Test
    void should_storeNormalizedEvent_withActorResolution() {
        VcsPullRequest pr = pr("pr-1", 1, null);
        when(client.reviewSignals(CREDENTIALS, repository, pr)).thenReturn(List.of(
                new VcsReviewSignal("r-1", "1001", "alice", false, ReviewKind.APPROVED, false, T0)));
        when(authors.resolveUserId(ORG, "1001")).thenReturn("user-alice");

        assertThat(contentSync.syncReviewEvents(context, List.of(pr), null)).isEqualTo(1);

        VcsReviewEvent saved = capture(VcsReviewEvent.class, reviewEventRepository);
        assertThat(saved.getKind()).isEqualTo(ReviewKind.APPROVED);
        assertThat(saved.getActorUserId()).isEqualTo("user-alice");
        assertThat(saved.getActorLogin()).isEqualTo("alice");
        assertThat(saved.getRepositoryId()).isEqualTo("repo-1");
        assertThat(saved.getPullRequestId()).isEqualTo("pr-1");
        assertThat(saved.getOccurredAt()).isEqualTo(T0);
    }

    @Test
    void should_keepStoredKind_when_reviewIsLaterDismissed() {
        VcsPullRequest pr = pr("pr-1", 1, null);
        VcsReviewEvent stored = new VcsReviewEvent("e-1", ORG, "pr-1", "repo-1", VcsProvider.GITHUB, "r-1",
                ReviewKind.APPROVED, T0);
        when(reviewEventRepository.findByOrganizationIdAndPullRequestIdAndExternalEventId(ORG, "pr-1", "r-1"))
                .thenReturn(Optional.of(stored));
        when(client.reviewSignals(CREDENTIALS, repository, pr)).thenReturn(List.of(
                new VcsReviewSignal("r-1", "1001", "alice", false, null, true, T0)));

        contentSync.syncReviewEvents(context, List.of(pr), null);

        assertThat(capture(VcsReviewEvent.class, reviewEventRepository).getKind()).isEqualTo(ReviewKind.APPROVED);
    }

    @Test
    void should_storeDismissedAsCommented_when_firstSeenDismissed() {
        VcsPullRequest pr = pr("pr-1", 1, null);
        when(client.reviewSignals(CREDENTIALS, repository, pr)).thenReturn(List.of(
                new VcsReviewSignal("r-2", "1001", "alice", false, null, true, T0)));

        contentSync.syncReviewEvents(context, List.of(pr), null);

        assertThat(capture(VcsReviewEvent.class, reviewEventRepository).getKind()).isEqualTo(ReviewKind.COMMENTED);
    }

    @Test
    void should_keepSyncingOtherPullRequests_when_signalsOfOneFail() {
        VcsPullRequest broken = pr("pr-1", 1, null);
        VcsPullRequest healthy = pr("pr-2", 2, null);
        when(client.reviewSignals(CREDENTIALS, repository, broken)).thenThrow(new RuntimeException("502"));
        when(client.reviewSignals(CREDENTIALS, repository, healthy)).thenReturn(List.of(
                new VcsReviewSignal("r-3", "1002", "bob", false, ReviewKind.COMMENTED, false, T0)));

        assertThat(contentSync.syncReviewEvents(context, List.of(broken, healthy), null)).isEqualTo(1);
    }

    private void givenRemotePullRequests(VcsRemotePullRequest... prs) {
        when(client.pullRequestsChangedSince(CREDENTIALS, repository, null, null)).thenReturn(List.of(prs));
    }

    private static VcsRemotePullRequest remote(String externalId, int number, PullRequestStatus status,
                                               String sourceBranch, String body, String title) {
        Instant mergedAt = status == PullRequestStatus.MERGED ? T0.plusSeconds(86_400) : null;
        return new VcsRemotePullRequest(externalId, number, title, body, status, "octocat", "58421", T0,
                T0.plusSeconds(3600), mergedAt, mergedAt, sourceBranch, "main", true, List.of("ui"));
    }

    private static VcsPullRequest pr(String id, int number, Instant externalUpdatedAt) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, "repo-1", VcsProvider.GITHUB, "ext-" + id, number, "t",
                PullRequestStatus.OPEN);
        pr.setExternalUpdatedAt(externalUpdatedAt);
        return pr;
    }

    @SuppressWarnings("unchecked")
    private static <T> T capture(Class<T> type, Object repository) {
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(type);
        if (repository instanceof VcsCommitRepository commits) verify(commits).save((VcsCommit) captor.capture());
        if (repository instanceof VcsReviewEventRepository events) verify(events).save((VcsReviewEvent) captor.capture());
        return captor.getValue();
    }
}
