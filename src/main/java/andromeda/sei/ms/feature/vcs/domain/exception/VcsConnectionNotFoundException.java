package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * The organization has no active connection for the provider. A conflict (409) rather than a
 * not-found: the request is valid, the organization's state does not allow it yet.
 */
public class VcsConnectionNotFoundException extends ResourceConflictException {

    public VcsConnectionNotFoundException(String provider) {
        super("VCS_CONNECTION_NOT_FOUND", "No active connection for provider: " + provider);
    }
}
