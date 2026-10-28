package andromeda.sei.ms.feature.vcs.application.port;

import java.util.Map;

/**
 * Hard delete of the data one repository produced (branches-por-vinculo D5): review events, pull
 * requests and commits of the {@code (organizationId, repositoryId)} pair. The repository document
 * and its binding stay. Used when the branch set or the pull request filter of a binding changes.
 */
public interface VcsRepositoryDataWiper {

    /** @return documents removed, by collection */
    Map<String, Long> wipeRepository(String organizationId, String repositoryId);
}
