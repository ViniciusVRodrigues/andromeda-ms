package andromeda.sei.ms.feature.provideraccount.application.dto;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;

import java.time.Instant;

/**
 * Self-service listing shape — token fields are deliberately absent (design.md D5/D11).
 */
public record ProviderAccountOutput(
        Provider provider,
        String externalLogin,
        Instant linkedAt,
        Instant expiresAt,
        boolean hasRefreshToken
) {
}
