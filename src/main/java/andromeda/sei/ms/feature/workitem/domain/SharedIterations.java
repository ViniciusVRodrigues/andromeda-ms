package andromeda.sei.ms.feature.workitem.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An Azure iteration shared by several teams is stored once per team: sprints are unique per
 * {@code (organizationId, externalId, teamId)}, so the "twins" of one iteration share
 * {@code projectId}, {@code externalId} and {@code path}. A work item only carries the iteration
 * path, so it is linked to exactly one twin — the <b>canonical</b> one — and every per-iteration
 * view (dashboards, sprint selector, {@code sprint_metrics}) must show the iteration once, through
 * that same twin, or it would list a phantom zero-effort copy of the sprint.
 *
 * <p>Canonical = earliest {@code createdAt} (nulls last), then lowest {@code teamId}, then lowest
 * {@code id}. Earliest-created first keeps the choice stable when a team is added to an iteration
 * later: the twin the existing work items are already linked to stays canonical. One comparator,
 * used everywhere — the sync that links items and the reads that look them up must agree.
 */
public final class SharedIterations {

    public static final Comparator<Sprint> CANONICAL_ORDER = Comparator
            .comparing(Sprint::getCreatedAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
            .thenComparing(Sprint::getTeamId, Comparator.nullsLast(Comparator.<String>naturalOrder()))
            .thenComparing(Sprint::getId, Comparator.nullsLast(Comparator.<String>naturalOrder()));

    private static final String KEY_SEPARATOR = "\u0000";

    private SharedIterations() {}

    /** The canonical sprint among the given ones (normally twins of one iteration); never throws. */
    public static Optional<Sprint> canonical(Collection<Sprint> sprints) {
        if (sprints == null) return Optional.empty();
        return sprints.stream().filter(Objects::nonNull).min(CANONICAL_ORDER);
    }

    /**
     * One sprint per iteration — the canonical twin — keeping the input order of the survivors, so
     * callers can sort before or after.
     */
    public static List<Sprint> distinct(Collection<Sprint> sprints) {
        if (sprints == null) return List.of();
        Map<String, Sprint> canonicalByKey = sprints.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(SharedIterations::iterationKey, Function.identity(),
                        (a, b) -> CANONICAL_ORDER.compare(a, b) <= 0 ? a : b));
        return sprints.stream().filter(Objects::nonNull)
                .filter(s -> canonicalByKey.get(iterationKey(s)) == s)
                .toList();
    }

    /** The canonical twin of {@code sprint} among {@code candidates} (itself when it has none). */
    public static Sprint canonicalTwin(Sprint sprint, Collection<Sprint> candidates) {
        Objects.requireNonNull(sprint, "sprint");
        String key = iterationKey(sprint);
        List<Sprint> twins = new ArrayList<>(List.of(sprint));
        if (candidates != null) {
            candidates.stream().filter(Objects::nonNull).filter(c -> key.equals(iterationKey(c))).forEach(twins::add);
        }
        return canonical(twins).orElse(sprint);
    }

    /** Identity of the iteration: project plus path (falling back to the external id, then the id). */
    static String iterationKey(Sprint sprint) {
        String iteration = sprint.getPath() != null ? sprint.getPath()
                : sprint.getExternalId() != null ? sprint.getExternalId()
                : sprint.getId();
        return sprint.getProjectId() + KEY_SEPARATOR + iteration;
    }
}
