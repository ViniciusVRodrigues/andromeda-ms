package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** The binding is syncing: changing its branches now would let the running sync write old-branch data (409). */
public class RepoSyncRunningException extends ResourceConflictException {

    public RepoSyncRunningException(String bindingId) {
        super("REPO_SYNC_RUNNING", "A sync is running for binding " + bindingId + "; try again shortly");
    }
}
