package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class GithubPatInvalidException extends BusinessRuleException {

    public GithubPatInvalidException(String message) {
        super("GITHUB_PAT_INVALID", message);
    }
}
