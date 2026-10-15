package andromeda.sei.ms.feature.usage.domain;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Append-only store of funnel events (pre-tenant, declared exception to soft delete and to the
 * {@code organizationId}-on-every-document rule; expired by TTL).
 */
public interface FunnelEventRepository {

    void insertAll(List<FunnelEvent> events);

    /**
     * Points the still-anonymous events of the funnel (same flow, {@code userId} null, received at or after
     * {@code since}) at the new account. Already-linked events are never touched. Returns how many changed.
     */
    long linkToAccount(String funnelId, FunnelFlow flow, String organizationId, String userId, Instant since,
                       Instant linkedAt);

    /**
     * Events linked to the organization with {@code from <= occurredAt < to}, ordered by {@code occurredAt}
     * (feeds the export merge). Backed by a database cursor: the caller MUST close the stream.
     */
    Stream<FunnelEvent> streamByOrganization(String organizationId, Instant from, Instant to);
}
