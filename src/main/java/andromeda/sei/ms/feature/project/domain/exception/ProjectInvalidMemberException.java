package andromeda.sei.ms.feature.project.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class ProjectInvalidMemberException extends BusinessRuleException {

    public ProjectInvalidMemberException(String userId) {
        super("PROJECT_INVALID_MEMBER", "User does not belong to this organization: " + userId);
    }
}
