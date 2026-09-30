package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzurePatConnectionInput;
import andromeda.sei.ms.feature.azure.application.port.AzureDevOpsAccessProbe;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import andromeda.sei.ms.feature.azure.domain.exception.AzurePatInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Connects the organization to Azure DevOps with a Personal Access Token (azure-org-connection).
 *
 * <p>Validation before anything is persisted: the URL shape ({@code AZURE_ORG_URL_INVALID}), then
 * the PAT against the organization's project list ({@code AZURE_PAT_INVALID} when denied). A PAT
 * without Code (Read) is still accepted — Boards does not need it — and is stored with
 * {@code reposAccess = false} so the UI can warn before any Azure Repos binding (design D6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigureAzureDevOpsPatUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final EncryptionService encryptionService;
    private final SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;
    private final AzureDevOpsAccessProbe accessProbe;

    public void execute(AzurePatConnectionInput input) {
        validateOrgUrl(input.adoOrganizationUrl());
        if (input.personalAccessToken() == null || input.personalAccessToken().isBlank()) {
            throw new AzurePatInvalidException("personalAccessToken must not be blank");
        }

        String orgId = TenantContextHolder.get();
        AzureConnection connection = candidate(orgId, input, encryptionService.encrypt(input.personalAccessToken()));
        if (accessProbe.projects(connection) != AzureProjectsAccess.GRANTED) {
            throw new AzurePatInvalidException(
                    "Azure DevOps rejected the Personal Access Token for " + input.adoOrganizationUrl()
                            + ": it must be valid for this organization with Work Items (Read) and Project and Team (Read)");
        }
        connection.setReposAccess(accessProbe.reposAccess(connection));

        AzureConnection saved = azureConnectionRepository.save(connection);

        try {
            syncAzureOrgLevelDataUseCase.execute(saved);
        } catch (Exception e) {
            log.warn("Org-level Azure sync failed after PAT connection for org={}: {}", orgId, e.getMessage(), e);
        }
    }

    /** The connection as it would be stored; not saved until the PAT is validated. */
    private AzureConnection candidate(String orgId, AzurePatConnectionInput input, String encryptedPat) {
        Instant now = Instant.now();
        Optional<AzureConnection> existing = azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId);
        if (existing.isEmpty()) {
            return new AzureConnection(
                    null, orgId, input.adoOrganizationUrl(),
                    null, null, null, null,
                    AzureConnectionStatus.CONNECTED,
                    null, 0, null, null,
                    true, now, now, null,
                    AzureAuthMethod.PAT, encryptedPat
            );
        }
        AzureConnection connection = existing.get();
        connection.setAuthMethod(AzureAuthMethod.PAT);
        connection.setAzureOrgUrl(input.adoOrganizationUrl());
        connection.setPersonalAccessToken(encryptedPat);
        connection.setAccessToken(null);
        connection.setRefreshToken(null);
        connection.setTokenExpiresAt(null);
        connection.setAuthorizedByUserId(null);
        connection.setStatus(AzureConnectionStatus.CONNECTED);
        connection.setConsecutiveFailures(0);
        connection.setLastErrorCode(null);
        connection.setLastErrorMessage(null);
        connection.setUpdatedAt(now);
        return connection;
    }

    private void validateOrgUrl(String adoOrganizationUrl) {
        if (adoOrganizationUrl == null || adoOrganizationUrl.isBlank()) {
            throw new AzureOrgUrlInvalidException("adoOrganizationUrl must not be blank");
        }
        if (!AzureOrgUrl.isValid(adoOrganizationUrl)) {
            throw new AzureOrgUrlInvalidException("adoOrganizationUrl must match " + AzureOrgUrl.EXPECTED_SHAPE);
        }
    }
}
