package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import org.springframework.stereotype.Component;

@Component
public class AzureConnectionDocumentMapper {

    public AzureConnection toDomain(AzureConnectionDocument doc) {
        AzureConnection connection = new AzureConnection(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getAzureOrgUrl(),
                doc.getAccessToken(),
                doc.getRefreshToken(),
                doc.getTokenExpiresAt(),
                doc.getAuthorizedByUserId(),
                doc.getStatus(),
                doc.getLastSuccessAt(),
                doc.getConsecutiveFailures(),
                doc.getLastErrorCode(),
                doc.getLastErrorMessage(),
                doc.isActive(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                doc.getVersion(),
                doc.getAuthMethod(),
                doc.getPersonalAccessToken()
        );
        connection.setReposAccess(doc.getReposAccess());
        connection.setOrgLevelSyncedAt(doc.getOrgLevelSyncedAt());
        return connection;
    }

    public AzureConnectionDocument toDocument(AzureConnection connection) {
        return AzureConnectionDocument.builder()
                .id(connection.getId())
                .organizationId(connection.getOrganizationId())
                .azureOrgUrl(connection.getAzureOrgUrl())
                .accessToken(connection.getAccessToken())
                .refreshToken(connection.getRefreshToken())
                .tokenExpiresAt(connection.getTokenExpiresAt())
                .authorizedByUserId(connection.getAuthorizedByUserId())
                .status(connection.getStatus())
                .lastSuccessAt(connection.getLastSuccessAt())
                .consecutiveFailures(connection.getConsecutiveFailures())
                .lastErrorCode(connection.getLastErrorCode())
                .lastErrorMessage(connection.getLastErrorMessage())
                .active(connection.isActive())
                .createdAt(connection.getCreatedAt())
                .updatedAt(connection.getUpdatedAt())
                .version(connection.getVersion())
                .authMethod(connection.getAuthMethod())
                .personalAccessToken(connection.getPersonalAccessToken())
                .reposAccess(connection.getReposAccess())
                .orgLevelSyncedAt(connection.getOrgLevelSyncedAt())
                .build();
    }
}
