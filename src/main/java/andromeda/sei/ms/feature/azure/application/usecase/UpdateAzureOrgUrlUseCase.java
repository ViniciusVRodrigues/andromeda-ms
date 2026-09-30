package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureConnectionStatusOutput;
import andromeda.sei.ms.feature.azure.application.port.AzureDevOpsAccessProbe;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureTokenRefreshFailedException;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fixes the organization URL of the existing Azure DevOps connection without reauthorizing
 * (azure-repos-provider task 1.3) — the way out for OAuth connections stored with the Entra issuer
 * as URL, flagged {@code ERROR / AZURE_ORG_URL_INVALID} by {@code AzureOrgUrlMigration}.
 *
 * <p>The new URL must have an accepted shape and the stored credential must list its projects;
 * otherwise {@code AZURE_ORG_URL_INVALID} and nothing changes. On success the connection is
 * {@code CONNECTED} again, its error cleared and {@code reposAccess} re-evaluated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateAzureOrgUrlUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureTokenRefreshService azureTokenRefreshService;
    private final AzureDevOpsAccessProbe accessProbe;
    private final SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;

    public AzureConnectionStatusOutput execute(String azureOrgUrl) {
        if (!AzureOrgUrl.isValid(azureOrgUrl)) {
            throw new AzureOrgUrlInvalidException("azureOrgUrl must match " + AzureOrgUrl.EXPECTED_SHAPE);
        }
        String orgId = TenantContextHolder.get();
        AzureConnection stored = azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));
        /* A token refresh saves the connection as CONNECTED: remember the state to restore it when the
         * new URL is rejected, or the fix form (shown for ERROR) would vanish with the URL still wrong. */
        AzureConnectionStatus previousStatus = stored.getStatus();
        String previousErrorCode = stored.getLastErrorCode();
        String previousErrorMessage = stored.getLastErrorMessage();
        AzureConnection connection = ready(stored);
        String previousUrl = connection.getAzureOrgUrl();
        connection.setAzureOrgUrl(AzureOrgUrl.normalize(azureOrgUrl));

        if (accessProbe.projects(connection) != AzureProjectsAccess.GRANTED) {
            connection.setAzureOrgUrl(previousUrl);
            if (connection.getStatus() != previousStatus
                    || !java.util.Objects.equals(connection.getLastErrorCode(), previousErrorCode)) {
                connection.setStatus(previousStatus);
                connection.setLastErrorCode(previousErrorCode);
                connection.setLastErrorMessage(previousErrorMessage);
                azureConnectionRepository.save(connection);
            }
            throw new AzureOrgUrlInvalidException("The connection's credential cannot list the projects of "
                    + azureOrgUrl + "; check the organization name");
        }
        connection.setStatus(AzureConnectionStatus.CONNECTED);
        connection.setConsecutiveFailures(0);
        connection.setLastErrorCode(null);
        connection.setLastErrorMessage(null);
        connection.setReposAccess(accessProbe.reposAccess(connection));
        AzureConnection saved = azureConnectionRepository.save(connection);
        try {
            syncAzureOrgLevelDataUseCase.execute(saved);
        } catch (Exception e) {
            log.warn("Org-level Azure sync failed after organization URL fix for org={}: {}", orgId, e.getMessage(), e);
        }
        return AzureConnectionStatusOutput.of(saved);
    }

    private AzureConnection ready(AzureConnection connection) {
        if (connection.getAuthMethod() == AzureAuthMethod.PAT) return connection;
        try {
            return azureTokenRefreshService.refreshIfNeeded(connection);
        } catch (RuntimeException e) {
            throw new AzureTokenRefreshFailedException(connection.getOrganizationId());
        }
    }
}
