package andromeda.sei.ms.feature.azure.domain;

import java.util.List;
import java.util.Optional;

public interface AzureOrgProjectRepository {

    List<AzureOrgProject> findAllByOrganizationIdAndActiveTrue(String organizationId);

    Optional<AzureOrgProject> findByOrganizationIdAndAzureProjectIdAndActiveTrue(
            String organizationId, String azureProjectId);

    AzureOrgProject save(AzureOrgProject project);

    void saveAll(List<AzureOrgProject> projects);
}
