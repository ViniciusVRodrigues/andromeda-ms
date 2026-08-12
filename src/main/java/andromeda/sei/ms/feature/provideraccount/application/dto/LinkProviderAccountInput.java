package andromeda.sei.ms.feature.provideraccount.application.dto;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;

import java.time.Instant;
import java.util.List;

public record LinkProviderAccountInput(
        String organizationId,
        String userId,
        Provider provider,
        String externalUserId,
        String externalLogin,
        String externalEmail,
        String accessToken,
        String refreshToken,
        Instant tokenExpiresAt,
        List<String> scopes
) {
}
