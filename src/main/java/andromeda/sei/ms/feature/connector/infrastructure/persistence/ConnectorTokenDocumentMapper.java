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
                doc.getVersion() != null ? doc.getVersion() : 0L,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public ConnectorTokenDocument toDocument(ConnectorToken token) {
        return ConnectorTokenDocument.builder()
                .id(token.getId())
                .organizationId(token.getOrganizationId())
                .userId(token.getUserId())
                .label(token.getLabel())
                .tokenHash(token.getTokenHash())
                .active(token.isActive())
                .version(token.getVersion())
                .createdAt(token.getCreatedAt())
                .updatedAt(token.getUpdatedAt())
                .build();
    }
}
