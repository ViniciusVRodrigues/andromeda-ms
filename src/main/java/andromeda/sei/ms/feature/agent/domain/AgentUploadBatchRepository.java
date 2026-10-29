package andromeda.sei.ms.feature.agent.domain;

import java.util.Optional;

/** Port of the stored batch responses. Tenant-scoped and per device. */
public interface AgentUploadBatchRepository {

    Optional<AgentUploadBatch> find(String organizationId, String deviceId, String batchId);

    /** Stores the batch; a concurrent save of the same batch (unique key) is silently ignored. */
    void save(AgentUploadBatch batch);
}
