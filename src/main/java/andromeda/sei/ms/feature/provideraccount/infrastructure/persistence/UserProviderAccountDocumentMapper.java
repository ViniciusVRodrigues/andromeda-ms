package andromeda.sei.ms.feature.provideraccount.infrastructure.persistence;

import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import org.springframework.stereotype.Component;

@Component
public class UserProviderAccountDocumentMapper {

    public UserProviderAccount toDomain(UserProviderAccountDocument doc) {
        return new UserProviderAccount(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getUserId(),
                doc.getProvider(),
                doc.getExternalUserId(),
                doc.getExternalLogin(),
                doc.getExternalEmail(),
                doc.getAccessTokenEncrypted(),
                doc.getRefreshTokenEncrypted(),
                doc.getTokenExpiresAt(),
                doc.getScopes(),
                doc.getLinkedAt(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public UserProviderAccountDocument toDocument(UserProviderAccount account) {
        return UserProviderAccountDocument.builder()
                .id(account.getId())
                .organizationId(account.getOrganizationId())
                .userId(account.getUserId())
                .provider(account.getProvider())
                .externalUserId(account.getExternalUserId())
                .externalLogin(account.getExternalLogin())
                .externalEmail(account.getExternalEmail())
                .accessTokenEncrypted(account.getAccessTokenEncrypted())
                .refreshTokenEncrypted(account.getRefreshTokenEncrypted())
                .tokenExpiresAt(account.getTokenExpiresAt())
                .scopes(account.getScopes())
                .linkedAt(account.getLinkedAt())
                .active(account.isActive())
                .version(account.getVersion())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
