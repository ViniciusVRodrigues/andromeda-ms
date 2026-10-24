package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * Azure DevOps answered 401/403 to the organization directory ({@code userentitlements}): the PAT
 * lacks "Member Entitlement Management (Read)". Callers log a WARN and continue with other sources.
 */
public class AzureEntitlementsForbiddenException extends ForbiddenOperationException {

    public static final String CODE = "AZURE_ENTITLEMENTS_FORBIDDEN";

    public AzureEntitlementsForbiddenException() {
        super(CODE, "Azure DevOps denied access to the organization user directory "
                + "(userentitlements). The token needs Member Entitlement Management (Read).");
    }
}
