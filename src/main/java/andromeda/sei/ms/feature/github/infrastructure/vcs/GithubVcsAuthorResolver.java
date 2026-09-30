package andromeda.sei.ms.feature.github.infrastructure.vcs;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * GitHub author resolution (github-integration spec "Author resolution via existing account
 * linking"): active {@code UserProviderAccount} (provider GITHUB, externalUserId = numeric author
 * id) first, legacy {@code User.githubId} second, {@code null} otherwise. The raw login is recorded
 * by the caller regardless.
 */
@Component
@RequiredArgsConstructor
public class GithubVcsAuthorResolver implements VcsAuthorResolver {

    private final UserProviderAccountRepository userProviderAccountRepository;
    private final UserRepository userRepository;

    @Override
    public VcsProvider provider() {
        return VcsProvider.GITHUB;
    }

    @Override
    public String resolveUserId(String organizationId, String actorExternalId) {
        if (actorExternalId == null || actorExternalId.isBlank()) return null;
        return userProviderAccountRepository
                .findByOrganizationIdAndProviderAndExternalUserId(organizationId, Provider.GITHUB, actorExternalId)
                .filter(UserProviderAccount::isActive)
                .map(UserProviderAccount::getUserId)
                .or(() -> userRepository.findByOrganizationIdAndGithubId(organizationId, actorExternalId)
                        .map(User::getId))
                .orElse(null);
    }
}
