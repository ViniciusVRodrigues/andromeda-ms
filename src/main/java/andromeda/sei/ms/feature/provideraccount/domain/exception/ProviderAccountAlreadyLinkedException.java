package andromeda.sei.ms.feature.provideraccount.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * The external provider account is already actively linked to a DIFFERENT Andromeda user
 * within the same organization (design.md D2) — one external account maps to at most one user.
 */
public class ProviderAccountAlreadyLinkedException extends ResourceConflictException {

    public ProviderAccountAlreadyLinkedException(String provider, String externalUserId) {
        super("PROVIDER_ACCOUNT_ALREADY_LINKED",
                "Provider account already linked to another user: provider=" + provider + " externalUserId=" + externalUserId);
    }
}
