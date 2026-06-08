package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AzureProjectBindingMongoRepository extends MongoRepository<AzureProjectBindingDocument, String> {

    Optional<AzureProjectBindingDocument> findByOrganizationIdAndProjectIdAndActiveTrue(
            String organizationId, String projectId);

    Optional<AzureProjectBindingDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<AzureProjectBindingDocument> findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(String organizationId);

    List<AzureProjectBindingDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);
}
