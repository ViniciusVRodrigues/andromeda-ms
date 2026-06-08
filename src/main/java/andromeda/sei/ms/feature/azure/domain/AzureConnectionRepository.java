package andromeda.sei.ms.feature.azure.domain;

import java.util.List;
import java.util.Optional;

public interface AzureConnectionRepository {

    Optional<AzureConnection> findByOrganizationIdAndActiveTrue(String organizationId);

    List<AzureConnection> findAllByStatusAndActiveTrue(AzureConnectionStatus status);

    AzureConnection save(AzureConnection connection);
}
