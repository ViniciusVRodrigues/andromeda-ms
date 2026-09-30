package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** No implementation of the provider ports is registered for this provider. */
public class VcsProviderUnsupportedException extends BusinessRuleException {

    public VcsProviderUnsupportedException(String provider) {
        super("VCS_PROVIDER_UNSUPPORTED", "Unsupported VCS provider: " + provider);
    }
}
