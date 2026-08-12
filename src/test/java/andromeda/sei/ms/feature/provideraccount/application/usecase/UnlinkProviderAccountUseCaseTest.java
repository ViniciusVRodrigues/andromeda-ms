package andromeda.sei.ms.feature.provideraccount.application.usecase;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountNotFoundException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnlinkProviderAccountUseCaseTest {

    @Mock private UserProviderAccountRepository providerAccountRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private UnlinkProviderAccountUseCase useCase;

    @Test
    void shouldSoftDeleteAccount_andClearGithubId_whenUnlinkingGithub() {
        UserProviderAccount account = account(Provider.GITHUB);
        when(providerAccountRepository.findByOrganizationIdAndUserIdAndProviderAndActiveTrue(
                "org-1", "user-1", Provider.GITHUB)).thenReturn(Optional.of(account));
        User user = userWithGithubId("58421");
        when(userRepository.findByIdAndOrganizationId("user-1", "org-1")).thenReturn(Optional.of(user));

        useCase.execute("org-1", "user-1", Provider.GITHUB);

        ArgumentCaptor<UserProviderAccount> accountCaptor = ArgumentCaptor.forClass(UserProviderAccount.class);
        verify(providerAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().isActive()).isFalse();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGithubId()).isNull();
    }

    @Test
    void shouldNotTouchAzureId_whenUnlinkingAzureDevOpsAccount() {
        UserProviderAccount account = account(Provider.AZURE_DEVOPS);
        when(providerAccountRepository.findByOrganizationIdAndUserIdAndProviderAndActiveTrue(
                "org-1", "user-1", Provider.AZURE_DEVOPS)).thenReturn(Optional.of(account));

        useCase.execute("org-1", "user-1", Provider.AZURE_DEVOPS);

        verify(providerAccountRepository).save(account);
        assertThat(account.isActive()).isFalse();
        verify(userRepository, never()).findByIdAndOrganizationId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowNotFound_whenNoActiveAccountForProvider() {
        when(providerAccountRepository.findByOrganizationIdAndUserIdAndProviderAndActiveTrue(
                "org-1", "user-1", Provider.GITHUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("org-1", "user-1", Provider.GITHUB))
                .isInstanceOf(ProviderAccountNotFoundException.class)
                .extracting(ex -> ((ProviderAccountNotFoundException) ex).getCode())
                .isEqualTo("PROVIDER_ACCOUNT_NOT_FOUND");
    }

    private UserProviderAccount account(Provider provider) {
        Instant now = Instant.now();
        return new UserProviderAccount("acc-1", "org-1", "user-1", provider, "ext-1",
                "login", null, "access-enc", null, null, List.of("scope"), now, true, 0L, now, now);
    }

    private User userWithGithubId(String githubId) {
        return new User("user-1", "org-1", "e@x.com", null, "Name", "role-1", null, githubId,
                null, null, null, true, true, 0L, Instant.now(), Instant.now());
    }
}
