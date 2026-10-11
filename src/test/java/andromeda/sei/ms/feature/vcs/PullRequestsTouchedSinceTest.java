package andromeda.sei.ms.feature.vcs;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dashboards-customizaveis (B1 performance) — the windowed pull request read of the v2 dashboards:
 * a pull request is in when it is open now, or was created or merged at or after {@code since}.
 * That is every pull request a window starting at {@code since} counts (created in it, merged in
 * it, open/stale now), so the dashboard figures stay the same while the old history is not read.
 */
class PullRequestsTouchedSinceTest {

    private static final String ORG = "org-1";
    private static final Instant SINCE = Instant.parse("2026-07-14T00:00:00Z");

    @Test
    void should_keepOpenCreatedOrMergedSince_andDropOldClosedHistory() {
        InMemoryVcsStore store = new InMemoryVcsStore();
        store.pullRequests.addAll(List.of(
                pr("old-open", PullRequestStatus.OPEN, "2025-01-01T00:00:00Z", null),
                pr("created-in", PullRequestStatus.CLOSED, "2026-08-01T00:00:00Z", null),
                pr("merged-in-created-before", PullRequestStatus.MERGED, "2026-06-01T00:00:00Z", "2026-07-20T00:00:00Z"),
                pr("old-merged", PullRequestStatus.MERGED, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z"),
                pr("old-closed", PullRequestStatus.CLOSED, "2026-01-01T00:00:00Z", null),
                inactive(pr("inactive-open", PullRequestStatus.OPEN, "2026-08-01T00:00:00Z", null)),
                other(pr("other-repo", PullRequestStatus.OPEN, "2026-08-01T00:00:00Z", null))));

        List<VcsPullRequest> found = store.pullRequestRepository.findActiveTouchedSince(ORG, List.of("repo-1"), SINCE);

        assertThat(found).extracting(VcsPullRequest::getId)
                .containsExactlyInAnyOrder("old-open", "created-in", "merged-in-created-before");
    }

    @Test
    void should_readNothing_withoutRepositories() {
        assertThat(new InMemoryVcsStore().pullRequestRepository.findActiveTouchedSince(ORG, List.of(), SINCE)).isEmpty();
    }

    private static VcsPullRequest pr(String id, PullRequestStatus status, String createdAt, String mergedAt) {
        VcsPullRequest pr = new VcsPullRequest(id, ORG, "repo-1", VcsProvider.GITHUB, "ext-" + id, 1, id, status);
        pr.setCreatedAt(Instant.parse(createdAt));
        pr.setMergedAt(mergedAt == null ? null : Instant.parse(mergedAt));
        return pr;
    }

    private static VcsPullRequest inactive(VcsPullRequest pr) {
        pr.setActive(false);
        return pr;
    }

    private static VcsPullRequest other(VcsPullRequest pr) {
        pr.setRepositoryId("repo-2");
        return pr;
    }
}
