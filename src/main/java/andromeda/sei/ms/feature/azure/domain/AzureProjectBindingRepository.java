package andromeda.sei.ms.feature.azure.domain;

import java.util.List;
import java.util.Optional;

public interface AzureProjectBindingRepository {

    Optional<AzureProjectBinding> findByOrganizationIdAndProjectIdAndActiveTrue(String organizationId, String projectId);

    Optional<AzureProjectBinding> findByOrganizationIdAndAzureProjectIdAndActiveTrue(
            String organizationId, String azureProjectId);

    Optional<AzureProjectBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<AzureProjectBinding> findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(String organizationId);

    List<AzureProjectBinding> findAllByOrganizationIdAndActiveTrue(String organizationId);

    AzureProjectBinding save(AzureProjectBinding binding);

    void saveAll(List<AzureProjectBinding> bindings);
}
