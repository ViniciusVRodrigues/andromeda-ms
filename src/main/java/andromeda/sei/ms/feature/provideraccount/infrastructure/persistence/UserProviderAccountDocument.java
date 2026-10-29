package andromeda.sei.ms.feature.provideraccount.infrastructure.persistence;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "user_provider_accounts")
@CompoundIndexes({
        @CompoundIndex(name = UserProviderAccountDocument.UK_EXTERNAL_ACTIVE,
                def = "{'organizationId': 1, 'provider': 1, 'externalUserId': 1}",
                unique = true, partialFilter = "{'active': true}"),
        @CompoundIndex(name = UserProviderAccountDocument.UK_USER_PROVIDER_ACTIVE,
                def = "{'organizationId': 1, 'userId': 1, 'provider': 1}",
                unique = true, partialFilter = "{'active': true}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProviderAccountDocument {

    public static final String UK_EXTERNAL_ACTIVE = "uk_org_provider_external_active";
    public static final String UK_USER_PROVIDER_ACTIVE = "uk_org_user_provider_active";

    @Id
    private String id;

    private String organizationId;
    private String userId;
    private Provider provider;
    private String externalUserId;
    private String externalLogin;
    private String externalEmail;
    private String accessTokenEncrypted;
    private String refreshTokenEncrypted;
    private Instant tokenExpiresAt;
    private List<String> scopes;
    private Instant linkedAt;

    private boolean active = true;
    @Version
    private Long version;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
