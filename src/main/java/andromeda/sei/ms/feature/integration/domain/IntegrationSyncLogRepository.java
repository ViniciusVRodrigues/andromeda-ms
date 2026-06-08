package andromeda.sei.ms.feature.integration.domain;

import java.util.List;

public interface IntegrationSyncLogRepository {

    IntegrationSyncLog save(IntegrationSyncLog log);

    List<IntegrationSyncLog> findByOrganizationIdAndIntegration(
            String organizationId, Integration integration, String projectId, int limit);
}
