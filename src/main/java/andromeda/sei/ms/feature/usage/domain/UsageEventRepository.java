package andromeda.sei.ms.feature.usage.domain;

import java.util.List;

/** Append-only store of usage events (declared exception to soft delete; expired by TTL). */
public interface UsageEventRepository {

    void insertAll(List<UsageEvent> events);
}
