package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** The branch list of a binding is empty, too long, repeated, blank, or the production branch is not in it (422). */
public class RepoBranchesInvalidException extends BusinessRuleException {

    public RepoBranchesInvalidException(String reason) {
        super("REPO_BRANCHES_INVALID", "Invalid branch selection: " + reason);
    }
}
