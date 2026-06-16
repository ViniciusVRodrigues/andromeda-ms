package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import org.springframework.stereotype.Component;

@Component
public class ConnectorTokenDocumentMapper {

    public ConnectorToken toDomain(ConnectorTokenDocument doc) {
        return new ConnectorToken(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getLabel(),
                doc.getTokenHash(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public ConnectorTokenDocument toDocument(ConnectorToken token) {
        var builder = ConnectorTokenDocument.builder()
                .organizationId(token.getOrganizationId())
                .userId(token.getUserId())
                .label(token.getLabel())
                .tokenHash(token.getTokenHash())
                .active(token.isActive())
                .createdAt(token.getCreatedAt())
                .updatedAt(token.getUpdatedAt());

        if (token.getId() != null) {
            builder.id(token.getId());
            builder.version(token.getVersion());
        }

        return builder.build();
    }
}
