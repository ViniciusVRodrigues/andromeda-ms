package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code authorUserId} for GitHub sync use cases (commits, pull requests, reviews)
 * (design.md D7): active {@code UserProviderAccount} (provider=GITHUB, externalUserId=numeric
 * author id) first, legacy {@code User.githubId} lookup second, {@code null} otherwise.
 * {@code authorGithubLogin} recording is unaffected -- this resolver only maps to a userId.
 */
@Component
@RequiredArgsConstructor
public class GithubAuthorResolver {

    private final UserProviderAccountRepository userProviderAccountRepository;
    private final UserRepository userRepository;

    public String resolve(String organizationId, Long authorGithubNumericId) {
        if (authorGithubNumericId == null) {
            return null;
        }
        String externalUserId = String.valueOf(authorGithubNumericId);

        return userProviderAccountRepository
                .findByOrganizationIdAndProviderAndExternalUserId(organizationId, Provider.GITHUB, externalUserId)
                .filter(account -> account.isActive())
                .map(account -> account.getUserId())
                .or(() -> userRepository.findByOrganizationIdAndGithubId(organizationId, externalUserId)
                        .map(user -> user.getId()))
                .orElse(null);
    }
}
