package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.WiqlFilter;

import java.time.Instant;
import java.util.List;

/**
 * Builds a paginated, filter-aware WIQL query selecting work item IDs ordered
 * ascending by System.Id. Pagination is driven by an exclusive lower bound
 * (lastSeenId) rather than offset/skip, keeping each page a bounded, indexed
 * range scan on the ADO side.
 */
public final class WiqlQueryBuilder {

    private WiqlQueryBuilder() {}

    public static String build(Instant since, Integer lastSeenId, WiqlFilter filter) {
        StringBuilder where = new StringBuilder();

        appendCondition(where, since != null, "[System.ChangedDate] >= '" + since + "'");
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
