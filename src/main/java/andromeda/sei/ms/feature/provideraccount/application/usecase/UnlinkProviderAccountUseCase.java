package andromeda.sei.ms.feature.provideraccount.application.usecase;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Soft-deletes the caller's active provider account; for {@code GITHUB} also clears
 * {@code User.githubId} so legacy attribution stops (design.md D6). {@code User.azureId} is
 * never touched here — Azure identity mapping stays admin-managed.
 */
@Component
@RequiredArgsConstructor
public class UnlinkProviderAccountUseCase {

    private final UserProviderAccountRepository providerAccountRepository;
    private final UserRepository userRepository;

    public void execute(String organizationId, String userId, Provider provider) {
        UserProviderAccount account = providerAccountRepository
                .findByOrganizationIdAndUserIdAndProviderAndActiveTrue(organizationId, userId, provider)
                .orElseThrow(() -> new ProviderAccountNotFoundException(provider.name()));

        account.setActive(false);
        providerAccountRepository.save(account);

        if (provider == Provider.GITHUB) {
            User user = userRepository.findByIdAndOrganizationId(userId, organizationId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            user.setGithubId(null);
            userRepository.save(user);
        }
    }
}
