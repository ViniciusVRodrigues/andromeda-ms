package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/**
 * The commits of a pull request could not be fetched from the provider right now (expired token,
 * exhausted rate limit, unreachable API). Answered with 502: the rest of the pull request detail
 * comes from synced data and stays valid, only this section is missing.
 */
public class PullRequestCommitsUnavailableException extends DomainException {

    public PullRequestCommitsUnavailableException(Integer pullRequestNumber, Throwable cause) {
        super("PR_COMMITS_UNAVAILABLE",
                "Could not fetch commits for pull request #" + pullRequestNumber + ": "
                        + (cause != null ? cause.getMessage() : "unknown error"));
    }
}
