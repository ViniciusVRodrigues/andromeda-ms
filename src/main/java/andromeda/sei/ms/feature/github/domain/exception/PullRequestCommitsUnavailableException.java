package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/**
 * The commits of a pull request could not be fetched from GitHub right now — an expired token,
 * an exhausted rate limit, or the API being unreachable.
 *
 * <p>Distinct from a not-found: the pull request exists and the rest of its detail, which comes
 * from synced data, is perfectly valid. Only this one section is missing, and the UI says so
 * instead of failing the whole panel.
 */
public class PullRequestCommitsUnavailableException extends DomainException {

    public PullRequestCommitsUnavailableException(int pullRequestNumber, Throwable cause) {
        super("GITHUB_PR_COMMITS_UNAVAILABLE",
                "Could not fetch commits for pull request #" + pullRequestNumber
                        + " from GitHub: " + cause.getMessage());
    }
}
