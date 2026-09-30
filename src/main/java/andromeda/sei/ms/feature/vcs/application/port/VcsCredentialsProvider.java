package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

import java.util.Optional;

/**
 * Organization credential of one provider (vcs-core D4). The sync still uses the organization's
 * credential — per-user OAuth for sync is a non-goal.
 */
public interface VcsCredentialsProvider {

    VcsProvider provider();

    /** @return the credential of the organization's active connection, or empty when there is none */
    Optional<VcsCredentials> credentials(String organizationId);
}
