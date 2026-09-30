package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** The OAuth refresh token of the Azure DevOps connection was rejected: the admin must reconnect. */
public class AzureTokenRefreshFailedException extends ResourceConflictException {

    public static final String CODE = "AZURE_TOKEN_REFRESH_FAILED";

    public AzureTokenRefreshFailedException(String organizationId) {
        super(CODE, "Could not refresh the Azure DevOps OAuth token of organization " + organizationId
                + "; reconnect Azure DevOps.");
    }
}
