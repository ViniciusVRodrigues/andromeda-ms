package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Pairing rule only (a building block of {@link CutoverRowDispositionPolicy}, not a bean): a row whose hour is before the pairing (truncated to the hour) is historical. */
public class PairingRowDispositionPolicy implements AgentRowDispositionPolicy {

    @Override
    public Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart) {
        Instant pairedAt = device.getPairedAt();
        if (pairedAt != null && bucketStart.isBefore(pairedAt.truncatedTo(ChronoUnit.HOURS))) {
            return Disposition.HISTORY;
        }
        return Disposition.LIVE;
    }
}
