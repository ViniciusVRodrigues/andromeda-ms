package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

import java.util.List;

/** A chosen branch does not exist in the provider (422). */
public class RepoBranchNotFoundException extends BusinessRuleException {

    public RepoBranchNotFoundException(List<String> branches) {
        super("REPO_BRANCH_NOT_FOUND", "Branch not found in the repository: " + String.join(", ", branches));
    }
}
