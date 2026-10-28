package andromeda.sei.ms.feature.workitem.application.classifier;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.Comparator;
import java.util.function.Function;

/**
 * The single ordering of the backlog (backlog-arvore-nivel D2), used by roots, orphans, children
 * and the filtered list: rank ascending (null last), then numeric externalId ascending (non-numeric
 * after, lexicographic), then id.
 */
public final class BacklogOrdering {

    private BacklogOrdering() {
    }

    public static Comparator<WorkItem> comparator(Function<WorkItem, Integer> rankOf) {
        return Comparator
                .comparing(rankOf, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
                .thenComparing(WorkItem::getExternalId, BacklogOrdering::compareExternalIds)
                .thenComparing(WorkItem::getId, Comparator.nullsLast(Comparator.<String>naturalOrder()));
    }

    /** Numeric ids first (by value), then the others in lexicographic order; null last. */
    public static int compareExternalIds(String a, String b) {
        if (a == null || b == null) {
            return a == b ? 0 : (a == null ? 1 : -1);
        }
        Long na = parse(a);
        Long nb = parse(b);
        if (na != null && nb != null) return Long.compare(na, nb);
        if (na != null) return -1;
        if (nb != null) return 1;
        return a.compareTo(b);
    }

    private static Long parse(String value) {
        if (value.isEmpty() || value.length() > 18) return null;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return null;
        }
        return Long.parseLong(value);
    }
}
