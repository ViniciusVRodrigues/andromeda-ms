package andromeda.sei.ms.feature.azure.application.port;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;

/**
 * Checks what an Azure DevOps credential can do in its organization, without persisting anything
 * (azure-org-connection, azure-repos-provider D6/D7). The connection passed in may be unsaved: the
 * PAT form and the OAuth callback probe before deciding what to store.
 */
public interface AzureDevOpsAccessProbe {

    /**
     * Lists the organization's projects ({@code _apis/projects}).
     *
     * @return {@code DENIED} on 401/403/404; other failures (5xx, network) propagate
     */
    AzureProjectsAccess projects(AzureConnection connection);

    /**
     * Lists the organization's Git repositories ({@code _apis/git/repositories}).
     *
     * @return {@code true} when allowed, {@code false} when denied (no Code (Read)), {@code null}
     *         when the check itself failed for another reason
     */
    Boolean reposAccess(AzureConnection connection);
}
