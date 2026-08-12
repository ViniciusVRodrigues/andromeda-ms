package andromeda.sei.ms.feature.provideraccount.presentation.dto;

import java.time.Instant;

public record ProviderAccountResponse(
        String provider,
        String externalLogin,
        Instant linkedAt,
        Instant expiresAt,
        boolean hasRefreshToken
) {
}
