package andromeda.sei.ms.feature.provideraccount.application.usecase;

import andromeda.sei.ms.feature.provideraccount.application.dto.ProviderAccountOutput;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListMyProviderAccountsUseCaseTest {

    @Mock private UserProviderAccountRepository repository;

    @InjectMocks
    private ListMyProviderAccountsUseCase useCase;

    @Test
    void shouldReturnAccountShape_withNullExpiresAtAndNoRefreshToken_forGithub() throws Exception {
        Instant linkedAt = Instant.parse("2024-01-01T00:00:00Z");
        UserProviderAccount account = new UserProviderAccount("acc-1", "org-1", "user-1", Provider.GITHUB,
                "58421", "octocat", "octocat@example.com", "access-enc", null, null,
                List.of("repo", "read:org"), linkedAt, true, 0L, linkedAt, linkedAt);
        when(repository.findAllByOrganizationIdAndUserIdAndActiveTrue("org-1", "user-1"))
                .thenReturn(List.of(account));

        List<ProviderAccountOutput> result = useCase.execute("org-1", "user-1");

        assertThat(result).hasSize(1);
        ProviderAccountOutput output = result.get(0);
        assertThat(output.provider()).isEqualTo(Provider.GITHUB);
        assertThat(output.externalLogin()).isEqualTo("octocat");
        assertThat(output.linkedAt()).isEqualTo(linkedAt);
        assertThat(output.expiresAt()).isNull();
        assertThat(output.hasRefreshToken()).isFalse();
        assertNoTokenFieldsExposed(output);
    }

    @Test
    void shouldSetHasRefreshTokenTrue_whenRefreshTokenPresent() {
        Instant now = Instant.now();
        UserProviderAccount account = new UserProviderAccount("acc-2", "org-1", "user-1", Provider.AZURE_DEVOPS,
                "ext-1", "login", null, "access-enc", "refresh-enc", now.plusSeconds(3600),
                List.of("user_impersonation"), now, true, 0L, now, now);
        when(repository.findAllByOrganizationIdAndUserIdAndActiveTrue("org-1", "user-1"))
                .thenReturn(List.of(account));

        List<ProviderAccountOutput> result = useCase.execute("org-1", "user-1");

        assertThat(result.get(0).hasRefreshToken()).isTrue();
        assertThat(result.get(0).expiresAt()).isEqualTo(now.plusSeconds(3600));
    }

    private void assertNoTokenFieldsExposed(ProviderAccountOutput output) throws Exception {
        for (Field field : output.getClass().getDeclaredFields()) {
            assertThat(field.getName().toLowerCase())
                    .doesNotContain("accesstoken")
                    .doesNotContain("refreshtokenencrypted");
        }
    }
}
