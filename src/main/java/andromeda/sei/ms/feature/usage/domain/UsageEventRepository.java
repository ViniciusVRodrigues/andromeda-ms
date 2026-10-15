package andromeda.sei.ms.feature.usage.domain;

import java.time.Instant;
import java.util.List;

/** Append-only store of usage events (declared exception to soft delete; expired by TTL). */
public interface UsageEventRepository {

    void insertAll(List<UsageEvent> events);

    /**
     * Events of the organization with {@code from <= occurredAt < to}, ordered by {@code occurredAt}
     * (feeds the export merge). Backed by a database cursor: the caller MUST close the stream.
     */
    java.util.stream.Stream<UsageEvent> streamByOrganization(String organizationId, Instant from, Instant to);
}
