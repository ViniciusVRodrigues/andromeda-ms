package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class OrganizationDeleteConfirmationMismatchException extends BusinessRuleException {

    public OrganizationDeleteConfirmationMismatchException() {
        super("ORGANIZATION_DELETE_CONFIRMATION_MISMATCH",
                "The confirmation does not match the organization slug");
    }
}
