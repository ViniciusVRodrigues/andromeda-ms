package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AzureOrgUserMongoRepository extends MongoRepository<AzureOrgUserDocument, String> {

    List<AzureOrgUserDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);

    Optional<AzureOrgUserDocument> findByOrganizationIdAndAzureIdAndActiveTrue(
            String organizationId, String azureId);
}
