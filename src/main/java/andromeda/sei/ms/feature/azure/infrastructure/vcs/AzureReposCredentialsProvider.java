package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.feature.azure.domain.exception.AzureTokenRefreshFailedException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Azure Repos uses the organization's Azure DevOps connection (azure-repos-integration "reuses the
 * Azure DevOps connection"): no separate credential. The token handed to the adapter is the
 * complete {@code Authorization} header ({@code Basic} for a PAT, {@code Bearer} for OAuth, the
 * access token refreshed first when it is about to expire), and {@code baseUrl} is the
 * organization URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureReposCredentialsProvider implements VcsCredentialsProvider {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final AzureAuthStrategyResolver authStrategyResolver;

    @Override
    public VcsProvider provider() {
        return VcsProvider.AZURE_REPOS;
    }

    /**
     * @throws AzureTokenRefreshFailedException the OAuth token could not be refreshed (the
     *                                          connection was flagged {@code EXPIRED})
     */
    @Override
    public Optional<VcsCredentials> credentials(String organizationId) {
        return azureConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .filter(connection -> connection.getStatus() != AzureConnectionStatus.DISCONNECTED)
                .map(connection -> {
                    /* Read before the refresh: refreshing saves the connection as CONNECTED, and the
                     * scheduled sync must still see an ERROR connection as such (Boards does the same). */
                    boolean inError = connection.getStatus() == AzureConnectionStatus.ERROR;
                    return toCredentials(organizationId, ready(connection), inError);
                });
    }

    private AzureConnection ready(AzureConnection connection) {
        if (connection.getAuthMethod() == AzureAuthMethod.PAT) return connection;
        try {
            return azureTokenRefreshService.refreshIfNeeded(connection);
        } catch (RuntimeException e) {
            log.warn("Azure OAuth token refresh failed for org={}: {}", connection.getOrganizationId(), e.getMessage());
            throw new AzureTokenRefreshFailedException(connection.getOrganizationId());
        }
    }

    private VcsCredentials toCredentials(String organizationId, AzureConnection connection, boolean inError) {
        String orgUrl = connection.getAzureOrgUrl() == null ? null : AzureOrgUrl.normalize(connection.getAzureOrgUrl());
        return new VcsCredentials(VcsProvider.AZURE_REPOS, organizationId, authStrategyResolver.resolve(connection),
                inError, orgUrl);
    }
}
