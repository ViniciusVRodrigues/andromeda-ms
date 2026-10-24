package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** O inicio do OAuth do Azure por JWT saiu: agora e por ticket de uso unico (HTTP 410). */
public class AzureConnectMovedException extends DomainException {

    public AzureConnectMovedException() {
        super("AZURE_CONNECT_MOVED",
                "Azure DevOps OAuth now starts from a one-time ticket: POST /api/v1/auth/oauth2/link-ticket "
                        + "with purpose AZURE_ORG_CONNECT, then open /api/v1/auth/oauth2/authorization/azure?ticket=...");
    }
}
