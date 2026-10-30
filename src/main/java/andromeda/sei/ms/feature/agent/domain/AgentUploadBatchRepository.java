package andromeda.sei.ms.feature.agent.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Port of the stored batch responses. Tenant-scoped and per device. A batch is claimed before it is
 * processed (no response yet = in progress), so a resend of the same {@code batchId} while the first
 * request still runs is not processed twice (agt10).
 */
public interface AgentUploadBatchRepository {

    /** Outcome of {@link #claim}. */
    sealed interface Claim {

        /** This request owns the batch and must {@link #complete} or {@link #release} it. */
        record Owned(Instant claimedAt) implements Claim {
        }

        /** The batch was already processed: answer the stored response. */
        record Done(UsageUploadResult response) implements Claim {
        }

        /** Another request is processing the batch right now. */
        record Running() implements Claim {
        }
    }

    /** The batch, processed or in progress ({@link AgentUploadBatch#getResponse()} null). */
    Optional<AgentUploadBatch> find(String organizationId, String deviceId, String batchId);

    /**
     * Claims the batch at {@code now} (millisecond precision). A claim older than {@code staleBefore}
     * whose request never finished (the server stopped mid-batch) is taken over.
     */
    Claim claim(String organizationId, String deviceId, String batchId, Instant now, Instant staleBefore,
                Instant expiresAt);

    /** Stores the response of a claimed batch; the first stored response stands. */
    void complete(String organizationId, String deviceId, String batchId, UsageUploadResult response);

    /** Drops a claim that failed before completing, if it is still the one taken at {@code claimedAt}. */
    void release(String organizationId, String deviceId, String batchId, Instant claimedAt);
}
