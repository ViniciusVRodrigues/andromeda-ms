package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.AgentUploadBatch;
import andromeda.sei.ms.feature.agent.domain.AgentUploadBatchRepository;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.RowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AgentUploadBatchRepositoryImpl implements AgentUploadBatchRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<AgentUploadBatch> find(String organizationId, String deviceId, String batchId) {
        Query query = new Query(Criteria.where("organizationId").is(organizationId)
                .and("deviceId").is(deviceId).and("batchId").is(batchId));
        return Optional.ofNullable(mongoTemplate.findOne(query, AgentUploadBatchDocument.class)).map(this::toDomain);
    }

    @Override
    public void save(AgentUploadBatch batch) {
        try {
            mongoTemplate.insert(toDocument(batch));
        } catch (DuplicateKeyException ignored) {
            // two requests of the same batch raced: the first stored response stands
        }
    }

    private AgentUploadBatchDocument toDocument(AgentUploadBatch b) {
        AgentUploadBatchDocument d = new AgentUploadBatchDocument();
        d.setOrganizationId(b.getOrganizationId());
        d.setDeviceId(b.getDeviceId());
        d.setBatchId(b.getBatchId());
        d.setExpiresAt(b.getExpiresAt());
        d.setCreatedAt(b.getCreatedAt());
        d.setUpdatedAt(b.getCreatedAt());
        UsageUploadResult r = b.getResponse();
        AgentUploadBatchDocument.ResponseDocument rd = new AgentUploadBatchDocument.ResponseDocument();
        rd.setBatchId(r.batchId());
        rd.setAccepted(r.accepted());
        rd.setDuplicate(r.duplicate());
        rd.setRejected(r.rejected());
        rd.setResults(r.results().stream().map(row -> {
            AgentUploadBatchDocument.RowDocument x = new AgentUploadBatchDocument.RowDocument();
            x.setKey(row.key());
            x.setStatus(row.status().name());
            x.setAllocation(row.allocation() == null ? null : row.allocation().name());
            x.setWorkItemId(row.workItemId());
            x.setTaskRef(row.taskRef());
            x.setReason(row.reason());
            x.setField(row.field());
            x.setRetryable(row.retryable());
            return x;
        }).toList());
        d.setResponse(rd);
        return d;
    }

    private AgentUploadBatch toDomain(AgentUploadBatchDocument d) {
        AgentUploadBatchDocument.ResponseDocument rd = d.getResponse();
        List<RowResult> rows = rd.getResults() == null ? List.of() : rd.getResults().stream()
                .map(x -> new RowResult(x.getKey(), UsageUploadResult.Status.valueOf(x.getStatus()),
                        x.getAllocation() == null ? null : UsageUploadResult.Allocation.valueOf(x.getAllocation()),
                        x.getWorkItemId(), x.getTaskRef(), x.getReason(), x.getField(), x.getRetryable()))
                .toList();
        AgentUploadBatch b = new AgentUploadBatch(d.getOrganizationId(), d.getDeviceId(), d.getBatchId(),
                new UsageUploadResult(rd.getBatchId(), rd.getAccepted(), rd.getDuplicate(), rd.getRejected(), rows),
                d.getCreatedAt(), d.getExpiresAt());
        b.setId(d.getId());
        b.setVersion(d.getVersion());
        return b;
    }
}
