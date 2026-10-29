package andromeda.sei.ms.feature.agent.application.attribution;

import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef;

import java.util.Optional;

/** Port to the version-control slice: the single project a remote repository is bound to. */
public interface RepositoryProjectPort {

    /**
     * Project id of the organization's active repository matching {@code remote} and its active
     * binding; empty when the repository is unknown, unbound, ambiguous (Azure name shared by more
     * than one repository) or bound to more than one project.
     */
    Optional<String> findProjectId(String organizationId, RemoteRepositoryRef remote);
}
