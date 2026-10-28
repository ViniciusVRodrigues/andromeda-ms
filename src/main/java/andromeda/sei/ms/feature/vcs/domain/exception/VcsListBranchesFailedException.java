package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** The provider failed while listing the branches of a repository (502). */
public class VcsListBranchesFailedException extends DomainException {

    public VcsListBranchesFailedException(Throwable cause) {
        super("VCS_LIST_BRANCHES_FAILED",
                "Could not list the branches: " + (cause != null ? cause.getMessage() : "unknown error"));
    }
}
