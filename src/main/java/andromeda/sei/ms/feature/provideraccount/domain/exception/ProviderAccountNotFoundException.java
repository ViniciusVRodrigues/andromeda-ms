package andromeda.sei.ms.feature.provideraccount.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class ProviderAccountNotFoundException extends ResourceNotFoundException {

    public ProviderAccountNotFoundException(String provider) {
        super("PROVIDER_ACCOUNT_NOT_FOUND", "No active provider account found for provider: " + provider);
    }
}
