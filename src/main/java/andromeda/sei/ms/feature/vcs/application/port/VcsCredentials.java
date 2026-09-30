package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

/**
 * Organization credential for one provider, as handed to its {@link VcsProviderClient}. Opaque to
 * the neutral use cases: only the adapter of the same provider interprets {@code token} and
 * {@code baseUrl}.
 *
 * @param token    decrypted secret (a PAT, an OAuth access token, or for Azure Repos the complete
 *                 {@code Authorization} header value), never logged
 * @param inError  the connection is flagged {@code ERROR}: the scheduled sync skips it, a manual
 *                 refresh still runs (on-demand-refresh D3)
 * @param baseUrl  provider endpoint of the organization (Azure DevOps organization URL);
 *                 {@code null} when the provider has a fixed endpoint (GitHub)
 */
public record VcsCredentials(VcsProvider provider, String organizationId, String token, boolean inError,
                             String baseUrl) {

    public VcsCredentials(VcsProvider provider, String organizationId, String token, boolean inError) {
        this(provider, organizationId, token, inError, null);
    }

    @Override
    public String toString() {
        return "VcsCredentials[provider=" + provider + ", organizationId=" + organizationId + ", inError=" + inError
                + ", baseUrl=" + baseUrl + "]";
    }
}
