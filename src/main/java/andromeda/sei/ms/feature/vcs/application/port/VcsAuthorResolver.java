package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

/** Maps a provider actor (commit/pull request author, reviewer) to an Andromeda user id. */
public interface VcsAuthorResolver {

    VcsProvider provider();

    /** @return the Andromeda user id, or {@code null} when the actor is not linked to anyone */
    String resolveUserId(String organizationId, String actorExternalId);

    /**
     * Same, with the actor's email as a fallback key when the provider exposes it (Azure DevOps
     * {@code uniqueName}, Git author email). Providers without an email fallback ignore it.
     */
    default String resolveUserId(String organizationId, String actorExternalId, String actorEmail) {
        return resolveUserId(organizationId, actorExternalId);
    }
}
