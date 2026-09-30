package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;

/**
 * The Azure DevOps credential cannot read Git repositories (azure-repos-integration "Code read
 * scope is validated"): 403 on the Azure Repos flows only. Boards keeps working with the same PAT.
 */
public class AzureReposScopeMissingException extends ForbiddenOperationException {

    public static final String CODE = "AZURE_REPOS_SCOPE_MISSING";

    public AzureReposScopeMissingException() {
        super(CODE, "The Azure DevOps credential cannot read Git repositories. Reissue the Personal Access "
                + "Token with the Code (Read) scope (or reconnect via OAuth) to use Azure Repos.");
    }
}
