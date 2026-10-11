package andromeda.sei.ms.feature.integration.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    Optional<IntegrationSyncLog> findByIdAndOrganizationId(String id, String organizationId);

    /**
     * Logs of one integration and of the given operations started at or after {@code startedFrom}
     * (inclusive), newest first. Per integration so it rides the
     * {@code {organizationId, integration, startedAt}} index.
     */
    List<IntegrationSyncLog> findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(
            String organizationId, Integration integration, Collection<SyncOperation> operations,
            Instant startedFrom);

    /** Logs filhos (um por binding) de um refresh/sync pai, sempre escopados pela organizacao. */
    List<IntegrationSyncLog> findAllByOrganizationIdAndParentSyncId(String organizationId, String parentSyncId);

    /** Ha algum log {@code RUNNING} de uma das operacoes para o projeto? Usado pelo indicador de frescura. */
    boolean existsRunningByOrganizationIdAndProjectIdAndOperationIn(
            String organizationId, String projectId, Collection<SyncOperation> operations);

    /**
     * Ha algum log {@code RUNNING} de uma das operacoes num dos projetos? {@code projectIds} nulo =
     * qualquer projeto da organizacao. Uma consulta so, para o indicador de frescura de varias telas.
     */
    boolean existsRunningByOrganizationIdAndProjectIdInAndOperationIn(
            String organizationId, Collection<String> projectIds, Collection<SyncOperation> operations);

    /**
     * Encerra como {@code FAILED} os logs que ficaram {@code RUNNING} de uma execucao anterior da
     * aplicacao (o executor e em memoria: um restart interrompe o trabalho sem terminal).
     *
     * @return quantos logs foram encerrados
     */
    long failRunningStartedBefore(Instant startedBefore, String errorCode, String errorMessage);
}
