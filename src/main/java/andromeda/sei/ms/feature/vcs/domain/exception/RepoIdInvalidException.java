package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/**
 * The repository identifier supplied at bind time is malformed (e.g. a GitHub free-text name not in
 * {@code owner/name} form) or missing. Rejected next to the input instead of at the first sync.
 */
public class RepoIdInvalidException extends BusinessRuleException {

    public RepoIdInvalidException(String value) {
        super("REPO_ID_INVALID", "Invalid repository identifier: " + value);
    }
}
