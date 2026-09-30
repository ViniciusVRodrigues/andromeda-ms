package andromeda.sei.ms.feature.vcs.application.port;

/**
 * A repository offered by the provider for binding (neutral input DTO).
 *
 * @param bindable the provider allows binding it (Azure Repos: not disabled and with a default
 *                 branch); the use case still marks repositories bound elsewhere as not bindable
 */
public record VcsBindableRepository(String externalRepoId, String displayName, String externalProjectId,
                                    String externalProjectName, String defaultBranch, boolean bindable) {

    public VcsBindableRepository(String externalRepoId, String displayName, String externalProjectId,
                                 String externalProjectName, String defaultBranch) {
        this(externalRepoId, displayName, externalProjectId, externalProjectName, defaultBranch, true);
    }
}
