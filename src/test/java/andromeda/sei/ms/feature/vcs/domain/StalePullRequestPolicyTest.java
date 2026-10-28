package andromeda.sei.ms.feature.vcs.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StalePullRequestPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void should_notBeStale_when_mergedWithoutReviewForMoreThan24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.MERGED, NOW.minusSeconds(48 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_closedWithoutReviewForMoreThan24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.CLOSED, NOW.minusSeconds(48 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_statusIsNull() {
        assertThat(StalePullRequestPolicy.isStale(pr(null, NOW.minusSeconds(48 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_beStale_when_openWithoutReviewForMoreThan24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600)), NOW)).isTrue();
    }

    @Test
    void should_notBeStale_when_openAndAlreadyReviewed() {
        VcsPullRequest pr = pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600));
        pr.setTimeToFirstReviewHours(3.0);
        assertThat(StalePullRequestPolicy.isStale(pr, NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_openDraft() {
        VcsPullRequest pr = pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600));
        pr.setDraft(true);
        assertThat(StalePullRequestPolicy.isStale(pr, NOW)).isFalse();
    }

    @Test
    void should_notBeStale_when_openForExactly24h() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.OPEN, NOW.minusSeconds(24 * 3600)), NOW)).isFalse();
    }

    @Test
    void should_beStale_when_openFor24AndAHalfHours() {
        assertThat(StalePullRequestPolicy.isStale(pr(PullRequestStatus.OPEN, NOW.minusSeconds(24 * 3600 + 1800)), NOW)).isTrue();
    }

    private static final Set<String> CHOSEN = Set.of("main", "staging");

    private static VcsPullRequest openOld(String source, String base) {
        VcsPullRequest pr = pr(PullRequestStatus.OPEN, NOW.minusSeconds(48 * 3600));
        pr.setSourceBranch(source);
        pr.setBaseBranch(base);
        return pr;
    }

    @Test
    void should_notBeStale_when_promotionBetweenChosenBranches() {
        assertThat(StalePullRequestPolicy.isStale(openOld("staging", "main"), NOW, CHOSEN)).isFalse();
    }

    @Test
    void should_beStale_when_featureIntoMain() {
        assertThat(StalePullRequestPolicy.isStale(openOld("feature/x", "main"), NOW, CHOSEN)).isTrue();
    }

    @Test
    void should_followTodaysRule_when_sourceOrBaseIsNull() {
        assertThat(StalePullRequestPolicy.isStale(openOld(null, "main"), NOW, CHOSEN)).isTrue();
        assertThat(StalePullRequestPolicy.isStale(openOld("staging", null), NOW, CHOSEN)).isTrue();
    }

    @Test
    void should_notChange_when_legacyBindingChoosesOnlyTheDefaultBranch() {
        assertThat(StalePullRequestPolicy.isStale(openOld("staging", "main"), NOW, Set.of("main"))).isTrue();
        assertThat(StalePullRequestPolicy.isStale(openOld("staging", "main"), NOW)).isTrue();
    }

    @Test
    void should_resolveChosenBranchesPerRepository_when_usingTheBindingMap() {
        VcsRepoBinding configured = new VcsRepoBinding("b1", "org", "p", "repo", VcsProvider.GITHUB, true);
        configured.setBranches(List.of("main", "staging"));
        VcsRepoBinding legacy = new VcsRepoBinding("b2", "org", "p", "other", VcsProvider.GITHUB, true);
        VcsRepository repo = new VcsRepository("repo", "org", VcsProvider.GITHUB, "e1", "repo");
        repo.setDefaultBranch("main");
        VcsRepository otherRepo = new VcsRepository("other", "org", VcsProvider.GITHUB, "e2", "other");
        otherRepo.setDefaultBranch("main");

        Map<String, Set<String>> byRepository = StalePullRequestPolicy
                .chosenBranchesByRepository(List.of(configured, legacy), List.of(repo, otherRepo));

        assertThat(byRepository.get("repo")).containsExactlyInAnyOrder("main", "staging");
        assertThat(byRepository.get("other")).containsExactly("main");
        assertThat(StalePullRequestPolicy.isStale(openOld("staging", "main"), NOW, byRepository)).isFalse();
    }

    private static VcsPullRequest pr(PullRequestStatus status, Instant createdAt) {
        VcsPullRequest pr = new VcsPullRequest("pr-1", "org", "repo", VcsProvider.GITHUB, "ext-1", 1, "PR", status);
        pr.setCreatedAt(createdAt);
        return pr;
    }
}
