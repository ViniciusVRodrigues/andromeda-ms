package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzurePatConnectionInput;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import andromeda.sei.ms.feature.azure.domain.exception.AzurePatInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ConfigureAzureDevOpsPatUseCase {

    private static final Pattern DEV_AZURE_COM_URL =
            Pattern.compile("^https://dev\\.azure\\.com/[^/\\s]+/?$");
    private static final Pattern VISUALSTUDIO_COM_URL =
            Pattern.compile("^https://[^/\\s.]+\\.visualstudio\\.com/?$");

    private final AzureConnectionRepository azureConnectionRepository;
    private final EncryptionService encryptionService;

    public void execute(AzurePatConnectionInput input) {
        validateOrgUrl(input.adoOrganizationUrl());
        if (input.personalAccessToken() == null || input.personalAccessToken().isBlank()) {
            throw new AzurePatInvalidException("personalAccessToken must not be blank");
        }

        String orgId = TenantContextHolder.get();
        String encryptedPat = encryptionService.encrypt(input.personalAccessToken());
        Instant now = Instant.now();

        Optional<AzureConnection> existing = azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId);

        AzureConnection connection;
        if (existing.isPresent()) {
            connection = existing.get();
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
        } else {
            connection = new AzureConnection(
                    null, orgId, input.adoOrganizationUrl(),
                    null, null, null, null,
                    AzureConnectionStatus.CONNECTED,
                    null, 0, null, null,
                    true, now, now, null,
                    AzureAuthMethod.PAT, encryptedPat
            );
        }

        azureConnectionRepository.save(connection);
    }

    private void validateOrgUrl(String adoOrganizationUrl) {
        if (adoOrganizationUrl == null || adoOrganizationUrl.isBlank()) {
            throw new AzureOrgUrlInvalidException("adoOrganizationUrl must not be blank");
        }
        boolean matches = DEV_AZURE_COM_URL.matcher(adoOrganizationUrl).matches()
                || VISUALSTUDIO_COM_URL.matcher(adoOrganizationUrl).matches();
        if (!matches) {
            throw new AzureOrgUrlInvalidException(
                    "adoOrganizationUrl must match https://dev.azure.com/{org} or https://{org}.visualstudio.com");
        }
    }
}
