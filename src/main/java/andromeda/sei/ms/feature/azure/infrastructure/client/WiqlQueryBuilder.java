package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.WiqlFilter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds a paginated, filter-aware WIQL query selecting work item IDs ordered
 * ascending by System.Id. Pagination is driven by an exclusive lower bound
 * (lastSeenId) rather than offset/skip, keeping each page a bounded, indexed
 * range scan on the ADO side.
 *
 * <p>The date filter uses {@code System.AuthorizedDate}, not {@code System.ChangedDate}.
 * Scripts that edit with {@code bypassRules=true} can write {@code ChangedDate} in the
 * past, so a {@code ChangedDate >= watermark} filter never sees those revisions.
 * {@code AuthorizedDate} is the server's own revision timestamp and is not overwritten
 * by bypassRules; on a normal edit it equals {@code ChangedDate}.
 */
public final class WiqlQueryBuilder {

    /**
     * {@code System.AuthorizedDate} is queried with <em>date precision</em>, so Azure
     * DevOps rejects any literal carrying a time component at all — with or without
     * sub-second digits — failing the whole query with HTTP 400
     * ("You cannot supply a time with the date when running a query using date
     * precision"). The bound is therefore always rendered as a bare {@code yyyy-MM-dd}
     * date in UTC, never interpolated straight from the Instant.
     *
     * <p>Truncating to the day widens the window to the start of that UTC day, so an
     * incremental sync re-fetches items already seen earlier in the same day. That
     * redundancy is deliberate: work item upserts are idempotent, and rounding the
     * bound down is the only direction that cannot silently drop items changed
     * between midnight and the previous sync.
     */
    private static final DateTimeFormatter WIQL_DATE = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private WiqlQueryBuilder() {}

    public static String build(Instant since, Integer lastSeenId, WiqlFilter filter) {
        StringBuilder where = new StringBuilder();

        appendCondition(where, since != null,
                "[System.AuthorizedDate] >= '" + (since != null ? WIQL_DATE.format(since) : "") + "'");
        appendCondition(where, lastSeenId != null, "[System.Id] > " + lastSeenId);

        if (filter != null) {
            List<String> types = filter.getWorkItemTypes();
            if (types != null && !types.isEmpty()) {
                String inClause = types.stream()
                        .map(t -> "'" + t.replace("'", "''") + "'")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                appendCondition(where, true, "[System.WorkItemType] IN (" + inClause + ")");
            }
            if (filter.getAreaPath() != null && !filter.getAreaPath().isBlank()) {
                appendCondition(where, true,
                        "[System.AreaPath] UNDER '" + filter.getAreaPath().replace("'", "''") + "'");
            }
            if (filter.getIterationPath() != null && !filter.getIterationPath().isBlank()) {
                appendCondition(where, true,
                        "[System.IterationPath] UNDER '" + filter.getIterationPath().replace("'", "''") + "'");
            }
        }

        String whereClause = where.length() > 0 ? " WHERE " + where : "";
        return "SELECT [System.Id] FROM WorkItems" + whereClause + " ORDER BY [System.Id] ASC";
    }

    private static void appendCondition(StringBuilder where, boolean condition, String clause) {
        if (!condition) return;
        if (where.length() > 0) where.append(" AND ");
        where.append(clause);
    }
}
