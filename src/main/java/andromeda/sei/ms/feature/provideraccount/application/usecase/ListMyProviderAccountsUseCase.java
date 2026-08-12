package andromeda.sei.ms.feature.provideraccount.application.usecase;

import andromeda.sei.ms.feature.provideraccount.application.dto.ProviderAccountOutput;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListMyProviderAccountsUseCase {

    private final UserProviderAccountRepository repository;

    public List<ProviderAccountOutput> execute(String organizationId, String userId) {
        return repository.findAllByOrganizationIdAndUserIdAndActiveTrue(organizationId, userId).stream()
                .map(account -> new ProviderAccountOutput(
                        account.getProvider(),
                        account.getExternalLogin(),
                        account.getLinkedAt(),
                        account.getTokenExpiresAt(),
                        account.getRefreshTokenEncrypted() != null))
                .toList();
    }
}
