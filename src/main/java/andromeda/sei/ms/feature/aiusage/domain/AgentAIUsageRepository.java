package andromeda.sei.ms.feature.aiusage.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of the usage rows sent by the local agent (agente-endpoint-v2). Identity of a row is
 * {@code (organizationId, deviceId, agentKey)}; every lookup is tenant-scoped.
 */
public interface AgentAIUsageRepository {

    /** Rows of the device by agent key, active or not. */
    Map<String, AIUsage> findByKeys(String organizationId, String deviceId, Collection<String> keys);

    /** Inserts a new row; empty when the unique key {@code (organizationId, deviceId, agentKey)} already exists. */
    Optional<AIUsage> insert(AIUsage usage);

    /**
     * Raises, field by field and atomically, input, output, cache read, cache write, thinking and
     * interactions to the given values (never lowers) and writes the cost. {@code allocationId},
     * attribution, {@code day} and {@code active} are not touched.
     */
    void raiseToMax(String organizationId, String id, AIUsage values);

    /** Lean rows (day, key and token counters) of the device within {@code [from, to]}, active or not. */
    List<AIUsage> findCountersByDeviceAndDayBetween(String organizationId, String deviceId,
                                                    LocalDate from, LocalDate to);

    /** Agent keys of the device on the day, active or not, in ascending order, at most {@code limit}. */
    List<String> findKeysByDeviceAndDay(String organizationId, String deviceId, LocalDate day, int limit);
}
