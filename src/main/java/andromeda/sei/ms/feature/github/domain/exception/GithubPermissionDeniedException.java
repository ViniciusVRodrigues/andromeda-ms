package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * A genuine 403 from GitHub (not rate-limit exhaustion) -- token lacks the required scope
 * or the resource is not accessible with the configured PAT.
 */
public class GithubPermissionDeniedException extends ForbiddenOperationException {

    public GithubPermissionDeniedException(String message) {
        super("GITHUB_PERMISSION_DENIED", message);
    }
}
