package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AzureConnectionMongoRepository extends MongoRepository<AzureConnectionDocument, String> {

    Optional<AzureConnectionDocument> findByOrganizationIdAndActiveTrue(String organizationId);

    List<AzureConnectionDocument> findAllByStatusAndActiveTrue(AzureConnectionStatus status);
}
