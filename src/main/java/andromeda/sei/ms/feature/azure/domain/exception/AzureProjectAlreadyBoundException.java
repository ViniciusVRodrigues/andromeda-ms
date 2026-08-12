package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * One external resource binds to at most one Andromeda project per organization
 * (design.md D3) — the ADO project is already actively bound to a DIFFERENT project.
 */
public class AzureProjectAlreadyBoundException extends ResourceConflictException {

    public AzureProjectAlreadyBoundException(String azureProjectId) {
        super("AZURE_PROJECT_ALREADY_BOUND",
                "Azure DevOps project already bound to another project: " + azureProjectId);
    }
}
