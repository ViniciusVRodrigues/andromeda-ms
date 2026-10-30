package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Decides what a new agent row is worth (design 6): live (allocated without review when it has a
 * task), historical (before the pairing: always goes to the queue) or superseded (a day that
 * belongs to the AIEF: stored inactive, counts as delivered, not in dashboards).
 *
 * <p>Extension point of the AIEF coexistence package: it decorates or replaces the default
 * {@link PairingRowDispositionPolicy} to add the cutover day and the AIEF check ({@code SUPERSEDED}).
 */
public interface AgentRowDispositionPolicy {

    enum Disposition { LIVE, HISTORY, SUPERSEDED }

    Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart);

    /** Same, for a row of this agent tool: the AIEF coexistence is per tool. */
    default Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart, String tool) {
        return classify(device, userId, day, bucketStart);
    }

    /** Hook after a row that counts was stored: the AIEF rows of the same tool and day without allocation go off. */
    default void afterStored(AgentDevice device, String userId, LocalDate day, String tool) {
    }

    /**
     * The policy for one upload batch (agt9): it may memoize its checks and its {@link #afterStored} per day
     * and tool while the batch runs. Use a new one per batch.
     */
    default AgentRowDispositionPolicy forBatch() {
        return this;
    }
}
