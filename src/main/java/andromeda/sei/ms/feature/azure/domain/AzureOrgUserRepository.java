package andromeda.sei.ms.feature.azure.domain;

import java.util.List;
import java.util.Optional;

public interface AzureOrgUserRepository {

    List<AzureOrgUser> findAllByOrganizationIdAndActiveTrue(String organizationId);

    Optional<AzureOrgUser> findByOrganizationIdAndAzureIdAndActiveTrue(String organizationId, String azureId);

    AzureOrgUser save(AzureOrgUser user);

    void saveAll(List<AzureOrgUser> users);
}
