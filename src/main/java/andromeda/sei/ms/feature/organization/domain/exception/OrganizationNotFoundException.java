package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class OrganizationNotFoundException extends ResourceNotFoundException {

    public OrganizationNotFoundException(String identifier) {
        super("ORG_NOT_FOUND", "Organization not found: " + identifier);
    }
}
