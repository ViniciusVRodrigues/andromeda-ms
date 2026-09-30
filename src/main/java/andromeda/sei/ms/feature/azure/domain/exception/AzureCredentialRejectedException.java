package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * Azure DevOps rejects the organization's stored credential altogether (not even the project list
 * is readable): the PAT expired or was revoked, or the OAuth grant was withdrawn.
 */
public class AzureCredentialRejectedException extends ResourceConflictException {

    public static final String CODE = "AZURE_CREDENTIAL_REJECTED";

    public AzureCredentialRejectedException() {
        super(CODE, "Azure DevOps rejected the organization's credential. Reconnect Azure DevOps "
                + "(new Personal Access Token or OAuth authorization).");
    }
}
