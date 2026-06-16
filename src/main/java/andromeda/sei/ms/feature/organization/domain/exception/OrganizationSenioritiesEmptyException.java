package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class OrganizationSenioritiesEmptyException extends BusinessRuleException {

    public OrganizationSenioritiesEmptyException() {
        super("ORGANIZATION_SENIORITIES_EMPTY", "Organization must have at least one seniority level");
    }
}
