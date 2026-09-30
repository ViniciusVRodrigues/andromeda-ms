package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Regra de saude da conexao aplicada ao fim de <b>qualquer</b> sync (design D3), nao so do
 * agendado: sucesso total leva a conexao a {@code CONNECTED}, zera as falhas e grava
 * {@code lastSuccessAt}; tres execucoes seguidas com falha levam a {@code ERROR}.
 *
 * <p>O cron continua pulando conexoes em {@code ERROR}, mas o refresh manual nao — e por isso
 * que o refresh e o caminho de recuperacao sem precisar reconfigurar o token.
 *
 * <p>A conexao e relida antes de gravar: o sync pode ter renovado o token (e consumido a
 * {@code version}) no meio do caminho, e salvar a instancia antiga falharia por lock otimista.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionHealthUpdater {

    static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final AzureConnectionRepository azureConnectionRepository;
    private final GithubConnectionRepository githubConnectionRepository;

    public void recordAzure(String organizationId, boolean allSucceeded) {
        azureConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId).ifPresent(connection -> {
            if (allSucceeded) {
                markAzureHealthy(connection);
            } else {
                markAzureFailure(connection);
            }
            azureConnectionRepository.save(connection);
        });
    }

    public void recordGithub(String organizationId, boolean allSucceeded) {
        githubConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId).ifPresent(connection -> {
            if (allSucceeded) {
                markGithubHealthy(connection);
            } else {
                markGithubFailure(connection);
            }
            githubConnectionRepository.save(connection);
        });
    }

    /**
     * Health of the connection behind a VCS provider: GitHub has its own connection; Azure Repos
     * shares the Azure DevOps one (azure-repos-provider).
     */
    public void recordVcs(VcsProvider provider, String organizationId, boolean allSucceeded) {
        switch (provider) {
            case GITHUB -> recordGithub(organizationId, allSucceeded);
            case AZURE_REPOS -> recordAzure(organizationId, allSucceeded);
        }
    }

    private void markAzureHealthy(AzureConnection connection) {
        connection.setConsecutiveFailures(0);
        connection.setLastSuccessAt(Instant.now());
        connection.setStatus(AzureConnectionStatus.CONNECTED);
    }

    private void markAzureFailure(AzureConnection connection) {
        connection.setConsecutiveFailures(connection.getConsecutiveFailures() + 1);
        if (connection.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            connection.setStatus(AzureConnectionStatus.ERROR);
            log.warn("Azure connection of org={} flipped to ERROR after {} consecutive failures",
                    connection.getOrganizationId(), connection.getConsecutiveFailures());
        }
    }

    private void markGithubHealthy(GithubConnection connection) {
        connection.setConsecutiveFailures(0);
        connection.setLastSuccessAt(Instant.now());
        connection.setStatus(GithubConnectionStatus.CONNECTED);
    }

    private void markGithubFailure(GithubConnection connection) {
        connection.setConsecutiveFailures(connection.getConsecutiveFailures() + 1);
        if (connection.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            connection.setStatus(GithubConnectionStatus.ERROR);
            log.warn("GitHub connection of org={} flipped to ERROR after {} consecutive failures",
                    connection.getOrganizationId(), connection.getConsecutiveFailures());
        }
    }
}
