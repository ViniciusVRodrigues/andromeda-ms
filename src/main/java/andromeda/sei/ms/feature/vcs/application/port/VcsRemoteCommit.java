package andromeda.sei.ms.feature.vcs.application.port;

import java.time.Instant;

/**
 * A commit as the provider reports it (neutral input DTO).
 *
 * @param authorLogin      provider account login; {@code null} for commits authored outside the provider
 * @param authorExternalId provider account id, used to resolve the Andromeda user
 * @param authorEmail      Git author email, a fallback for author resolution; may be {@code null}
 */
public record VcsRemoteCommit(String sha, String message, String authorLogin, String authorExternalId,
                              Instant authoredAt, String authorEmail) {

    public VcsRemoteCommit(String sha, String message, String authorLogin, String authorExternalId,
                           Instant authoredAt) {
        this(sha, message, authorLogin, authorExternalId, authoredAt, null);
    }
}
