package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AzureOrgProjectMongoRepository extends MongoRepository<AzureOrgProjectDocument, String> {

    List<AzureOrgProjectDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);

    Optional<AzureOrgProjectDocument> findByOrganizationIdAndAzureProjectIdAndActiveTrue(
            String organizationId, String azureProjectId);
}
