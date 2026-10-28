package andromeda.sei.ms.feature.vcs.domain;

import andromeda.sei.ms.shared.domain.metrics.Ratios;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * When an open pull request counts as stalled: nobody has reviewed it and it has been waiting
 * longer than the threshold.
 *
 * <p>Only a pull request with status OPEN can be stale: a merged or closed one is no longer waiting
 * for anybody.
 *
 * <p>Lives here so the team dashboard and the repository health summary cannot drift apart. The
 * threshold is the review SLA (review-metrics), and drafts are never stale: a draft is not waiting
 * for review yet.
 *
 * <p>A promotion pull request (staging into main, both among the branches chosen on the binding) is
 * a release step, not review work waiting for somebody: it is never stale. With a branch unknown on
 * either side the rule stays the one above.
 */
public final class StalePullRequestPolicy {

    public static final long STALE_PR_THRESHOLD_HOURS = ReviewMetricsPolicy.REVIEW_SLA_HOURS;

    private StalePullRequestPolicy() {}

    public static boolean isStale(VcsPullRequest pr, Instant now) {
        return isStale(pr, now, Set.of());
    }

    /** Repository id to the branches chosen on its binding, for {@link #isStale(VcsPullRequest, Instant, Map)}. */
    public static Map<String, Set<String>> chosenBranchesByRepository(Collection<VcsRepoBinding> bindings,
                                                                    Collection<VcsRepository> repositories) {
        Map<String, String> defaults = new HashMap<>();
        if (repositories != null) {
            repositories.stream().filter(r -> r != null && r.getId() != null)
                    .forEach(r -> defaults.putIfAbsent(r.getId(), r.getDefaultBranch()));
        }
        Map<String, Set<String>> byRepository = new HashMap<>();
        if (bindings != null) {
            for (VcsRepoBinding binding : bindings) {
                String repositoryId = binding.getRepositoryId();
                if (repositoryId == null) continue;
                byRepository.putIfAbsent(repositoryId, Set.copyOf(binding.effectiveBranches(defaults.get(repositoryId))));
            }
        }
        return byRepository;
    }

    public static boolean isStale(VcsPullRequest pr, Instant now, Map<String, Set<String>> chosenByRepository) {
        if (pr == null) return false;
        Set<String> chosen = chosenByRepository == null ? null : chosenByRepository.get(pr.getRepositoryId());
        return isStale(pr, now, chosen == null ? Set.<String>of() : chosen);
    }

    /** {@code chosenBranches}: the branches chosen on the binding of the pull request's repository. */
    public static boolean isStale(VcsPullRequest pr, Instant now, Set<String> chosenBranches) {
        if (pr == null || pr.getCreatedAt() == null) return false;
        if (pr.getStatus() != PullRequestStatus.OPEN) return false;
        if (pr.isDraft()) return false;
        if (pr.getTimeToFirstReviewHours() != null) return false;
        if (isPromotion(pr, chosenBranches)) return false;
        // Fractional hours, like the SLA eligibility of ReviewMetricsPolicy: both must agree at 24.5h.
        return Ratios.hours(pr.getCreatedAt(), now) > STALE_PR_THRESHOLD_HOURS;
    }

    private static boolean isPromotion(VcsPullRequest pr, Set<String> chosenBranches) {
        if (chosenBranches == null || pr.getSourceBranch() == null || pr.getBaseBranch() == null) return false;
        return chosenBranches.contains(pr.getSourceBranch()) && chosenBranches.contains(pr.getBaseBranch());
    }
}
