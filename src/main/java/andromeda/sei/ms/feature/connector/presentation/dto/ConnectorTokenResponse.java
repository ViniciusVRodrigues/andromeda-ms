package andromeda.sei.ms.feature.connector.presentation.dto;

import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenOutput;

import java.time.Instant;

public record ConnectorTokenResponse(
        String id,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        String rawToken  // non-null only on the creation response
) {
    public static ConnectorTokenResponse from(ConnectorTokenOutput output) {
        return new ConnectorTokenResponse(
                output.id(),
                output.label(),
                output.active(),
                output.createdAt(),
                output.updatedAt(),
                output.rawToken()
        );
    }
}
