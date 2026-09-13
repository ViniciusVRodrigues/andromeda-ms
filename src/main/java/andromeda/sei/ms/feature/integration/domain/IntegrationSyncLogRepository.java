package andromeda.sei.ms.feature.integration.domain;

import java.util.List;

public interface IntegrationSyncLogRepository {

    IntegrationSyncLog save(IntegrationSyncLog log);

    List<IntegrationSyncLog> findByOrganizationIdAndIntegration(
            String organizationId, Integration integration, String projectId, int limit);

    /**
     * Hard delete dos logs de uma integracao na organizacao. Usado pelo wipe, que limpa o
     * proprio historico — por isso o registro de auditoria do wipe e gravado DEPOIS desta
     * chamada, senao ele mesmo seria apagado.
     *
     * @return quantos documentos foram removidos
     */
    long deleteByOrganizationIdAndIntegration(String organizationId, Integration integration);
}
