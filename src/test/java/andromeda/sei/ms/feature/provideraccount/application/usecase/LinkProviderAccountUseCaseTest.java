package andromeda.sei.ms.feature.provideraccount.application.usecase;

import andromeda.sei.ms.feature.provideraccount.application.dto.LinkProviderAccountInput;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountAlreadyLinkedException;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkProviderAccountUseCaseTest {

    @Mock private UserProviderAccountRepository repository;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private LinkProviderAccountUseCase useCase;

    @Test
    void shouldEncryptTokensBeforeSave_whenLinkingNewAccount() {
        LinkProviderAccountInput input = input("org-1", "user-1", "58421", "access-plain", "refresh-plain");
        when(repository.findByOrganizationIdAndProviderAndExternalUserId("org-1", Provider.GITHUB, "58421"))
                .thenReturn(Optional.empty());
        when(repository.findByOrganizationIdAndUserIdAndProvider("org-1", "user-1", Provider.GITHUB))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("access-plain")).thenReturn("access-enc");
        when(encryptionService.encrypt("refresh-plain")).thenReturn("refresh-enc");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(input);

        ArgumentCaptor<UserProviderAccount> captor = ArgumentCaptor.forClass(UserProviderAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAccessTokenEncrypted()).isEqualTo("access-enc");
        assertThat(captor.getValue().getRefreshTokenEncrypted()).isEqualTo("refresh-enc");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
    }

    @Test
    void shouldRefreshExistingRow_whenSameUserRelinks() {
        UserProviderAccount existing = account("acc-1", "org-1", "user-1", "58421", true);
        LinkProviderAccountInput input = input("org-1", "user-1", "58421", "new-access", null);
        when(repository.findByOrganizationIdAndProviderAndExternalUserId("org-1", Provider.GITHUB, "58421"))
                .thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("new-access")).thenReturn("new-access-enc");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(input);

        ArgumentCaptor<UserProviderAccount> captor = ArgumentCaptor.forClass(UserProviderAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("acc-1");
        assertThat(captor.getValue().getAccessTokenEncrypted()).isEqualTo("new-access-enc");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void shouldTakeoverSoftDeletedRow_whenLinkingAbandonedExternalAccount() {
        UserProviderAccount softDeleted = account("acc-1", "org-1", "user-old", "58421", false);
        LinkProviderAccountInput input = input("org-1", "user-new", "58421", "access-plain", null);
        when(repository.findByOrganizationIdAndProviderAndExternalUserId("org-1", Provider.GITHUB, "58421"))
                .thenReturn(Optional.of(softDeleted));
        when(encryptionService.encrypt("access-plain")).thenReturn("access-enc");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(input);

        ArgumentCaptor<UserProviderAccount> captor = ArgumentCaptor.forClass(UserProviderAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("acc-1");
        assertThat(captor.getValue().getUserId()).isEqualTo("user-new");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void shouldThrow409_whenExternalAccountActivelyLinkedToAnotherUser() {
        UserProviderAccount ownedByAnother = account("acc-1", "org-1", "user-other", "58421", true);
        LinkProviderAccountInput input = input("org-1", "user-1", "58421", "access-plain", null);
        when(repository.findByOrganizationIdAndProviderAndExternalUserId("org-1", Provider.GITHUB, "58421"))
                .thenReturn(Optional.of(ownedByAnother));

        assertThatThrownBy(() -> useCase.execute(input))
                .isInstanceOf(ProviderAccountAlreadyLinkedException.class)
                .extracting(ex -> ((ProviderAccountAlreadyLinkedException) ex).getCode())
                .isEqualTo("PROVIDER_ACCOUNT_ALREADY_LINKED");
    }

    private LinkProviderAccountInput input(String orgId, String userId, String externalUserId,
                                            String accessToken, String refreshToken) {
        return new LinkProviderAccountInput(orgId, userId, Provider.GITHUB, externalUserId,
                "octocat", "octocat@example.com", accessToken, refreshToken, null, List.of("repo"));
    }

    private UserProviderAccount account(String id, String orgId, String userId, String externalUserId, boolean active) {
        Instant now = Instant.now();
        return new UserProviderAccount(id, orgId, userId, Provider.GITHUB, externalUserId,
                "old-login", "old@example.com", "old-access-enc", null, null, List.of("repo"),
                now, active, 0L, now, now);
    }
}
