package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsProviderUnsupportedException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves the port implementations of a provider (vcs-core D4). Adding a provider means
 * registering one bean of each port; a provider missing any of them is unsupported and fails with
 * {@code VCS_PROVIDER_UNSUPPORTED}.
 */
@Component
public class VcsProviderRegistry {

    private final Map<VcsProvider, VcsProviderClient> clients;
    private final Map<VcsProvider, VcsAuthorResolver> authorResolvers;
    private final Map<VcsProvider, VcsCredentialsProvider> credentialsProviders;

    public VcsProviderRegistry(List<VcsProviderClient> clients, List<VcsAuthorResolver> authorResolvers,
                               List<VcsCredentialsProvider> credentialsProviders) {
        this.clients = index(clients, VcsProviderClient::provider);
        this.authorResolvers = index(authorResolvers, VcsAuthorResolver::provider);
        this.credentialsProviders = index(credentialsProviders, VcsCredentialsProvider::provider);
    }

    public VcsProviderClient client(VcsProvider provider) {
        return require(clients, provider);
    }

    public VcsAuthorResolver authorResolver(VcsProvider provider) {
        return require(authorResolvers, provider);
    }

    public VcsCredentialsProvider credentialsProvider(VcsProvider provider) {
        return require(credentialsProviders, provider);
    }

    public boolean supports(VcsProvider provider) {
        return provider != null && clients.containsKey(provider) && authorResolvers.containsKey(provider)
                && credentialsProviders.containsKey(provider);
    }

    private static <T> T require(Map<VcsProvider, T> byProvider, VcsProvider provider) {
        T implementation = provider == null ? null : byProvider.get(provider);
        if (implementation == null) {
            throw new VcsProviderUnsupportedException(String.valueOf(provider));
        }
        return implementation;
    }

    private static <T> Map<VcsProvider, T> index(List<T> implementations, Function<T, VcsProvider> providerOf) {
        Map<VcsProvider, T> byProvider = new EnumMap<>(VcsProvider.class);
        implementations.forEach(implementation -> byProvider.put(providerOf.apply(implementation), implementation));
        return byProvider;
    }
}
