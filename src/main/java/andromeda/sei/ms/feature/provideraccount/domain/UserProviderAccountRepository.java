package andromeda.sei.ms.feature.provideraccount.domain;

import java.util.List;
import java.util.Optional;

public interface UserProviderAccountRepository {

    UserProviderAccount save(UserProviderAccount account);

    Optional<UserProviderAccount> findByOrganizationIdAndUserIdAndProviderAndActiveTrue(
            String organizationId, String userId, Provider provider);

    /**
     * Includes inactive rows — the link use case needs to see soft-deleted rows to implement
     * takeover semantics (design.md D2); active-only filtering happens in the use case logic.
     */
    Optional<UserProviderAccount> findByOrganizationIdAndProviderAndExternalUserId(
            String organizationId, Provider provider, String externalUserId);

    /**
     * Includes inactive rows, for the same reason as above — reusing the user's own row when
     * relinking a different external account under the same provider (design.md D2).
     */
    Optional<UserProviderAccount> findByOrganizationIdAndUserIdAndProvider(
            String organizationId, String userId, Provider provider);

    List<UserProviderAccount> findAllByOrganizationIdAndUserIdAndActiveTrue(
            String organizationId, String userId);
}
