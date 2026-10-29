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
}
