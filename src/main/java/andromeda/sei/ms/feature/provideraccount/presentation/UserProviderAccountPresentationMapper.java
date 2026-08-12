package andromeda.sei.ms.feature.provideraccount.presentation;

import andromeda.sei.ms.feature.provideraccount.application.dto.ProviderAccountOutput;
import andromeda.sei.ms.feature.provideraccount.presentation.dto.ProviderAccountResponse;
import org.springframework.stereotype.Component;

@Component
public class UserProviderAccountPresentationMapper {

    public ProviderAccountResponse toResponse(ProviderAccountOutput output) {
        return new ProviderAccountResponse(
                output.provider().name(),
                output.externalLogin(),
                output.linkedAt(),
                output.expiresAt(),
                output.hasRefreshToken());
    }
}
