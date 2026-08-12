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
 */
public final class WiqlQueryBuilder {

    /**
     * Azure DevOps only accepts WIQL DateTime literals with second precision
     * ({@code yyyy-MM-ddTHH:mm:ssZ}). {@code Instant#toString()} emits sub-second
     * digits whenever they are non-zero (which is always the case for a timestamp
     * captured with {@code Instant.now()} and round-tripped through MongoDB), and
     * ADO rejects the whole query with HTTP 400 / VS402512 when they are present —
     * so the bound is always formatted through this formatter, never interpolated
     * straight from the Instant.
     */
    private static final DateTimeFormatter WIQL_DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private WiqlQueryBuilder() {}

    public static String build(Instant since, Integer lastSeenId, WiqlFilter filter) {
        StringBuilder where = new StringBuilder();

        appendCondition(where, since != null,
                "[System.ChangedDate] >= '" + (since != null ? WIQL_DATE_TIME.format(since) : "") + "'");
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
