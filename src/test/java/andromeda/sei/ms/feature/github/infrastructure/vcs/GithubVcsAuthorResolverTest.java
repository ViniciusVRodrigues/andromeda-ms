package andromeda.sei.ms.feature.github.infrastructure.vcs;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubVcsAuthorResolverTest {

    @Mock private UserProviderAccountRepository userProviderAccountRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private GithubVcsAuthorResolver resolver;

    @Test
    void shouldResolveViaActiveProviderAccount_whenOneMatches() {
        UserProviderAccount account = providerAccount("user-42", true);
        when(userProviderAccountRepository.findByOrganizationIdAndProviderAndExternalUserId(
                "org-1", Provider.GITHUB, "58421")).thenReturn(Optional.of(account));

        String result = resolver.resolveUserId("org-1", "58421");

        assertThat(result).isEqualTo("user-42");
        verify(userRepository, never()).findByOrganizationIdAndGithubId(any(), any());
    }

    @Test
    void shouldFallBackToLegacyGithubId_whenProviderAccountInactiveOrAbsent() {
        when(userProviderAccountRepository.findByOrganizationIdAndProviderAndExternalUserId(
                "org-1", Provider.GITHUB, "58421")).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndGithubId("org-1", "58421"))
                .thenReturn(Optional.of(userWithId("user-legacy")));

        String result = resolver.resolveUserId("org-1", "58421");

        assertThat(result).isEqualTo("user-legacy");
    }

    @Test
    void shouldFallBackToLegacy_whenProviderAccountExistsButInactive() {
        UserProviderAccount inactive = providerAccount("user-99", false);
        when(userProviderAccountRepository.findByOrganizationIdAndProviderAndExternalUserId(
                "org-1", Provider.GITHUB, "58421")).thenReturn(Optional.of(inactive));
        when(userRepository.findByOrganizationIdAndGithubId("org-1", "58421"))
                .thenReturn(Optional.of(userWithId("user-legacy")));

        String result = resolver.resolveUserId("org-1", "58421");

        assertThat(result).isEqualTo("user-legacy");
    }

    @Test
    void shouldReturnNull_whenNeitherProviderAccountNorLegacyMatch() {
        when(userProviderAccountRepository.findByOrganizationIdAndProviderAndExternalUserId(
                "org-1", Provider.GITHUB, "99999")).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndGithubId("org-1", "99999")).thenReturn(Optional.empty());

        String result = resolver.resolveUserId("org-1", "99999");

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNull_whenAuthorGithubNumericIdIsNull() {
        String result = resolver.resolveUserId("org-1", null);

        assertThat(result).isNull();
        verify(userProviderAccountRepository, never())
                .findByOrganizationIdAndProviderAndExternalUserId(any(), any(), any());
    }

    private UserProviderAccount providerAccount(String userId, boolean active) {
        Instant now = Instant.now();
        return new UserProviderAccount("acc-1", "org-1", userId, Provider.GITHUB, "58421",
                "octocat", "octocat@example.com", "enc-token", null, null, java.util.List.of("repo"),
                now, active, 0L, now, now);
    }

    private User userWithId(String id) {
        return new User(id, "org-1", "e@x.com", null, "Name", "role-1", null, "58421",
                null, null, null, true, true, 0L, Instant.now(), Instant.now());
    }
}
