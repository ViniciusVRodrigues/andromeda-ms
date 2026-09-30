package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsProviderUnsupportedException;

import java.util.Optional;

/**
 * The single mapping between a VCS provider and the {@link Integration} its sync targets, logs and
 * wipes are filed under. Azure Repos shares the Azure DevOps connection, so its repository syncs
 * are logged under {@link Integration#AZURE_DEVOPS} (azure-repos-provider task 6.1).
 */
public final class VcsIntegrations {

    private VcsIntegrations() {}

    public static Integration integrationOf(VcsProvider provider) {
        if (provider == null) throw new VcsProviderUnsupportedException("null");
        return switch (provider) {
            case GITHUB -> Integration.GITHUB;
            case AZURE_REPOS -> Integration.AZURE_DEVOPS;
        };
    }

    /** The VCS provider whose data is filed under the integration, if any. */
    public static Optional<VcsProvider> providerOf(Integration integration) {
        if (integration == null) return Optional.empty();
        return switch (integration) {
            case GITHUB -> Optional.of(VcsProvider.GITHUB);
            case AZURE_DEVOPS -> Optional.of(VcsProvider.AZURE_REPOS);
            default -> Optional.empty();
        };
    }
}
