package andromeda.sei.ms.feature.user.presentation;

import andromeda.sei.ms.feature.user.application.dto.UserOutput;
import andromeda.sei.ms.feature.user.domain.AzureIdentitySource;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.presentation.dto.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserPresentationMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");

    @Test
    void shouldCarryAzureIdentitySourceAndLinkedAt_fromDomainToResponse() {
        User u = new User("u1", "org-1", "a@x.com", "h", "Ana", "r", null, null, null, null, null, true, true, 1L, NOW, NOW);
        u.setAzureId("az-1");
        u.setAzureIdentitySource(AzureIdentitySource.AUTO_EMAIL);
        u.setAzureIdentityLinkedAt(NOW);

        UserResponse response = new UserPresentationMapper().toResponse(UserOutput.from(u));

        assertThat(response.azureIdentitySource()).isEqualTo(AzureIdentitySource.AUTO_EMAIL);
        assertThat(response.azureIdentityLinkedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldLeaveThemNull_whenUserIsNotLinked() {
        User u = new User("u1", "org-1", "a@x.com", "h", "Ana", "r", null, null, null, null, null, true, true, 1L, NOW, NOW);

        UserResponse response = new UserPresentationMapper().toResponse(UserOutput.from(u));

        assertThat(response.azureIdentitySource()).isNull();
        assertThat(response.azureIdentityLinkedAt()).isNull();
    }
}
