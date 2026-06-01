package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

public class OrganizationSlugConflictException extends ResourceConflictException {

    public OrganizationSlugConflictException(String slug) {
        super("ORG_SLUG_CONFLICT", "Slug already in use: " + slug);
    }
}
