package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/**
 * Signals that GitHub's secondary (abuse) rate limit was hit -- distinct from the primary
 * rate limit, which is handled via reactive retry rather than a thrown exception.
 */
public class GithubSecondaryRateLimitException extends BusinessRuleException {

    public GithubSecondaryRateLimitException(String message) {
        super("GITHUB_SECONDARY_RATE_LIMIT", message);
    }
}
