package andromeda.sei.ms.feature.vcs.domain;

import java.util.Set;

/**
 * Outcome of a bulk authorship attribution: how many rows got an owner and in which repositories,
 * so the caller can recalculate only the metrics that changed.
 */
public record VcsAttribution(long modified, Set<String> repositoryIds) {

    public static VcsAttribution none() {
        return new VcsAttribution(0, Set.of());
    }
}
