package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureOAuthCallbackInput;
import andromeda.sei.ms.feature.azure.application.port.AzureDevOpsAccessProbe;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores the Azure DevOps OAuth connection after the Microsoft callback (azure-oauth-connection,
 * azure-repos-provider D7). The organization URL is the one the admin informed at authorization
 * start — never a token claim (the old {@code iss} fallback stored the Entra issuer).
 *
 * <ul>
 *   <li>URL missing or malformed → {@code AZURE_ORG_URL_INVALID}, nothing stored.</li>
 *   <li>Project list allowed with the token → {@code CONNECTED}, {@code reposAccess} computed.</li>
 *   <li>Project list denied → {@code AZURE_ORG_URL_INVALID}, and the connection is not marked
 *       {@code CONNECTED}. When there was no working connection, the tokens are kept with status
 *       {@code ERROR} / {@code lastErrorCode = AZURE_ORG_URL_INVALID}, so the admin can fix the URL
 *       without reauthorizing ({@code UpdateAzureOrgUrlUseCase}). A connection that is currently
 *       {@code CONNECTED} is left untouched instead of being replaced by a broken one.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteAzureOAuthConnectionUseCase {

    static final String ORG_URL_INVALID = "AZURE_ORG_URL_INVALID";
    static final String ORG_URL_UNREACHABLE_MESSAGE =
            "The authorized account cannot list the projects of this organization. Inform the right Azure "
                    + "DevOps organization URL (" + AzureOrgUrl.EXPECTED_SHAPE + ").";

    private final AzureConnectionRepository azureConnectionRepository;
    private final EncryptionService encryptionService;
    private final AzureDevOpsAccessProbe accessProbe;
    private final SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;

    public AzureConnection execute(AzureOAuthCallbackInput input) {
        Objects.requireNonNull(input.organizationId(), "organizationId");
        if (!AzureOrgUrl.isValid(input.orgUrl())) {
            throw new AzureOrgUrlInvalidException("The authorization did not carry a valid organization URL ("
                    + AzureOrgUrl.EXPECTED_SHAPE + "); start the connection again informing it");
        }
        Optional<AzureConnection> existing = azureConnectionRepository.findByOrganizationIdAndActiveTrue(
                input.organizationId());
        boolean hadWorkingConnection = existing.map(c -> c.getStatus() == AzureConnectionStatus.CONNECTED).orElse(false);
        AzureConnection connection = candidate(input, existing);

        if (accessProbe.projects(connection) != AzureProjectsAccess.GRANTED) {
            if (!hadWorkingConnection) {
                connection.setStatus(AzureConnectionStatus.ERROR);
                connection.setLastErrorCode(ORG_URL_INVALID);
                connection.setLastErrorMessage(ORG_URL_UNREACHABLE_MESSAGE);
                azureConnectionRepository.save(connection);
            }
            throw new AzureOrgUrlInvalidException(ORG_URL_UNREACHABLE_MESSAGE);
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
            log.warn("Org-level Azure sync failed after OAuth connection for org={}: {}",
                    input.organizationId(), e.getMessage(), e);
        }
        return saved;
    }

    private AzureConnection candidate(AzureOAuthCallbackInput input, Optional<AzureConnection> existing) {
        Instant now = Instant.now();
        String accessToken = encryptionService.encrypt(input.accessToken());
        String refreshToken = input.refreshToken() == null ? null : encryptionService.encrypt(input.refreshToken());
        String orgUrl = AzureOrgUrl.normalize(input.orgUrl());
        AzureConnection connection = existing.orElseGet(() -> new AzureConnection(
                null, input.organizationId(), orgUrl, null, null, null, null, AzureConnectionStatus.ERROR,
                null, 0, null, null, true, now, now, null, AzureAuthMethod.OAUTH, null));
        connection.setAuthMethod(AzureAuthMethod.OAUTH);
        connection.setAzureOrgUrl(orgUrl);
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(refreshToken);
        connection.setTokenExpiresAt(input.tokenExpiresAt());
        connection.setAuthorizedByUserId(input.userId());
        connection.setPersonalAccessToken(null);
        connection.setUpdatedAt(now);
        return connection;
    }
}
