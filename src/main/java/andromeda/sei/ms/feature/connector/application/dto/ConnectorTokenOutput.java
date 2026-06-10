package andromeda.sei.ms.feature.connector.application.dto;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;

import java.time.Instant;

public record ConnectorTokenOutput(
        String id,
        String organizationId,
        String userId,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        String rawToken  // non-null only on creation; null on all subsequent reads
) {
    public static ConnectorTokenOutput from(ConnectorToken token) {
        return new ConnectorTokenOutput(
                token.getId(),
                token.getOrganizationId(),
                token.getUserId(),
                token.getLabel(),
                token.isActive(),
                token.getCreatedAt(),
                token.getUpdatedAt(),
                null
        );
    }

    public static ConnectorTokenOutput fromWithRawToken(ConnectorToken token, String rawToken) {
        return new ConnectorTokenOutput(
                token.getId(),
                token.getOrganizationId(),
                token.getUserId(),
                token.getLabel(),
                token.isActive(),
                token.getCreatedAt(),
                token.getUpdatedAt(),
                rawToken
        );
    }
}
