package andromeda.sei.ms.feature.azure.application.port;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource;

import java.util.Collection;

/**
 * Registers, in batch, Azure identities seen outside the org directory/teams (work items, pull
 * requests) into {@code azure_org_users}, merged by source precedence (perfil-integracoes D9).
 */
public interface AzureOrgUserCollector {

    void collect(String organizationId, Collection<ObservedIdentity> identities);

    /** {@code uniqueName} is the email or principal name as Azure reported it; {@code source} is WORKITEM or PR. */
    record ObservedIdentity(String azureId, String displayName, String uniqueName, AzureOrgUserSource source) {}
}
