package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import org.springframework.stereotype.Component;

@Component
public class GithubConfigDocumentMapper {

    public GithubConnection toDomain(GithubConfigDocument doc) {
        if (doc == null) return null;
        GithubConnection connection = new GithubConnection(
                doc.getId(), doc.getOrganizationId(), doc.getPersonalAccessToken(),
                doc.getAuthorizedByUserId(), doc.getStatus(), doc.getLastSuccessAt(),
                doc.getConsecutiveFailures(), doc.getLastErrorCode(), doc.getLastErrorMessage(),
                doc.isActive(), doc.getCreatedAt(), doc.getUpdatedAt(), doc.getVersion());
        return connection;
    }

    public GithubConfigDocument toDocument(GithubConnection connection) {
        if (connection == null) return null;
        return GithubConfigDocument.builder()
                .id(connection.getId())
                .organizationId(connection.getOrganizationId())
                .personalAccessToken(connection.getPersonalAccessToken())
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
                .build();
    }
}
