package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.user.domain.AzureIdentitySource;
import andromeda.sei.ms.feature.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserDocumentMapperAzureSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private final UserDocumentMapper mapper = new UserDocumentMapper();

    private User user() {
        return new User("user-1", "org-A", "ana@acme.com", "hash", "Ana", "role-1", null, null, "az-1", "a@az.com",
                null, true, true, 3L, NOW, NOW);
    }

    @Test
    void mapper_shouldCarryAzureIdentitySourceAndLinkedAtInBothDirections() {
        User user = user();
        user.setAzureIdentitySource(AzureIdentitySource.AUTO_EMAIL);
        user.setAzureIdentityLinkedAt(NOW);

        UserDocument doc = mapper.toDocument(user);
        assertThat(doc.getAzureIdentitySource()).isEqualTo(AzureIdentitySource.AUTO_EMAIL);
        assertThat(doc.getAzureIdentityLinkedAt()).isEqualTo(NOW);

        User back = mapper.toDomain(doc);
        assertThat(back.getAzureIdentitySource()).isEqualTo(AzureIdentitySource.AUTO_EMAIL);
        assertThat(back.getAzureIdentityLinkedAt()).isEqualTo(NOW);
    }

    @Test
    void mapper_shouldKeepSourceNullWhenAbsent() {
        User back = mapper.toDomain(mapper.toDocument(user()));
        assertThat(back.getAzureIdentitySource()).isNull();
        assertThat(back.getAzureIdentityLinkedAt()).isNull();
    }
}
