package andromeda.sei.ms.feature.github.infrastructure.vcs;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** GitHub ISO-8601 timestamps; an absent or unparseable value reads as {@code null}. */
final class GithubInstants {

    private GithubInstants() {}

    static Instant parse(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
