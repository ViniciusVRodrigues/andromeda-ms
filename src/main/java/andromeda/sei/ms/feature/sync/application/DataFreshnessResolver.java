package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Resolve o {@link DataFreshness} de um projeto: o sync mais antigo entre os bindings que
 * alimentam as telas e se ha refresh rodando.
 *
 * <p>So entram bindings {@code active && syncEnabled}: um binding com sync desligado nunca e
 * atualizado pelo refresh, e conta-lo deixaria o indicador preso em "atualizado ha N dias" mesmo
 * depois de um refresh bem-sucedido.
 */
@Component
@RequiredArgsConstructor
public class DataFreshnessResolver {

    /** Pai do refresh e filhos por binding: cobre refresh de projeto, trigger manual e agendado. */
    private static final List<SyncOperation> RUNNING_OPERATIONS =
            List.of(SyncOperation.PROJECT_REFRESH, SyncOperation.SYNC_BINDING);

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final VcsRepoBindingRepository vcsRepoBindingRepository;
    private final IntegrationSyncLogRepository syncLogRepository;

    /**
     * @return frescura do projeto; {@code null} quando a leitura nao e de um projeto (ex.: dashboard
     *         sem {@code projectId}), ja que o refresh e sempre por projeto
     */
    public DataFreshness resolve(String organizationId, String projectId) {
        if (projectId == null) {
            return null;
        }
        return new DataFreshness(oldestSyncAt(organizationId, projectId),
                syncLogRepository.existsRunningByOrganizationIdAndProjectIdAndOperationIn(
                        organizationId, projectId, RUNNING_OPERATIONS));
    }

    private Instant oldestSyncAt(String organizationId, String projectId) {
        Stream<Instant> azure = azureProjectBindingRepository
                .findByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId).stream()
                .filter(AzureProjectBinding::isSyncEnabled)
                .map(AzureProjectBinding::getLastSyncAt);
        Stream<Instant> repositories = vcsRepoBindingRepository
                .findAllActiveByOrganizationIdAndProjectId(organizationId, projectId).stream()
                .filter(VcsRepoBinding::isSyncEnabled)
                .map(VcsRepoBinding::getLastSyncAt);
        return Stream.concat(azure, repositories)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }
}
