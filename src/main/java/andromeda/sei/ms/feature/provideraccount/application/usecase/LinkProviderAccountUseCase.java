package andromeda.sei.ms.feature.provideraccount.application.usecase;

import andromeda.sei.ms.feature.provideraccount.application.dto.LinkProviderAccountInput;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountAlreadyLinkedException;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.shared.application.security.VcsIdentityLinked;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Upsert/takeover semantics for linking a per-user provider account (design.md D2):
 * <ul>
 *     <li>an active row for {@code (org, provider, externalUserId)} owned by the caller -&gt; refresh it;</li>
 *     <li>an active row for {@code (org, provider, externalUserId)} owned by ANOTHER user -&gt; 409;</li>
 *     <li>an inactive row for {@code (org, provider, externalUserId)} -&gt; reactivate it, pointed at the
 *         linking user (takeover of an abandoned link);</li>
 *     <li>otherwise, reuse the caller's own inactive row for {@code (org, userId, provider)} if one exists
 *         (relinking under a different external account), else insert a new row.</li>
 * </ul>
 * Tokens are encrypted via {@link EncryptionService} before persistence — plaintext tokens never
 * reach the repository or logs.
 */
@Component
@RequiredArgsConstructor
public class LinkProviderAccountUseCase {

    private final UserProviderAccountRepository repository;
    private final EncryptionService encryptionService;
    private final ApplicationEventPublisher eventPublisher;

    public UserProviderAccount execute(LinkProviderAccountInput input) {
        Optional<UserProviderAccount> byExternalAccount = repository
                .findByOrganizationIdAndProviderAndExternalUserId(
                        input.organizationId(), input.provider(), input.externalUserId());

        UserProviderAccount target;
        if (byExternalAccount.isPresent()) {
            UserProviderAccount existing = byExternalAccount.get();
            if (existing.isActive() && !existing.getUserId().equals(input.userId())) {
                throw new ProviderAccountAlreadyLinkedException(
                        input.provider().name(), input.externalUserId());
            }
            // active row owned by the caller (re-link) OR inactive row (takeover) — reuse it.
            target = existing;
        } else {
            target = repository
                    .findByOrganizationIdAndUserIdAndProvider(
                            input.organizationId(), input.userId(), input.provider())
                    .orElseGet(this::newAccount);
        }

        Instant now = Instant.now();

        target.setOrganizationId(input.organizationId());
        target.setUserId(input.userId());
        target.setProvider(input.provider());
        target.setExternalUserId(input.externalUserId());
        target.setExternalLogin(input.externalLogin());
        target.setExternalEmail(input.externalEmail());
        target.setAccessTokenEncrypted(encryptionService.encrypt(input.accessToken()));
        target.setRefreshTokenEncrypted(
                input.refreshToken() != null ? encryptionService.encrypt(input.refreshToken()) : null);
        target.setTokenExpiresAt(input.tokenExpiresAt());
        target.setScopes(input.scopes());
        target.setLinkedAt(now);
        target.setActive(true);

        UserProviderAccount saved = repository.save(target);
        if (input.provider() == Provider.GITHUB) {
            eventPublisher.publishEvent(new VcsIdentityLinked(input.organizationId(), input.userId()));
        }
        return saved;
    }

    private UserProviderAccount newAccount() {
        return new UserProviderAccount(
                null, null, null, null, null, null, null,
                null, null, null, null, null,
                true, null, null, null);
    }
}
