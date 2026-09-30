package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import org.springframework.stereotype.Component;

/**
 * First step of the OAuth connection (azure-repos-provider D7): the admin informs the Azure DevOps
 * organization URL before authorizing, because the Entra token says nothing about which
 * organization it is for. The URL must have one of the accepted shapes; it then travels through
 * the authorization {@code state} and is validated against the organization after the callback.
 */
@Component
public class StartAzureOAuthUseCase {

    /**
     * @return the organization URL, normalized (no trailing slash)
     * @throws AzureOrgUrlInvalidException when absent or not an Azure DevOps Services organization URL
     */
    public String execute(String orgUrl) {
        if (!AzureOrgUrl.isValid(orgUrl)) {
            throw new AzureOrgUrlInvalidException("orgUrl must match " + AzureOrgUrl.EXPECTED_SHAPE);
        }
        return AzureOrgUrl.normalize(orgUrl);
    }
}
