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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AgentUploadBatchRepositoryImpl implements AgentUploadBatchRepository {

    private final MongoTemplate mongoTemplate;

    private static Criteria key(String organizationId, String deviceId, String batchId) {
        return Criteria.where("organizationId").is(organizationId).and("deviceId").is(deviceId)
                .and("batchId").is(batchId);
    }

    @Override
    public Optional<AgentUploadBatch> find(String organizationId, String deviceId, String batchId) {
        return Optional.ofNullable(mongoTemplate.findOne(new Query(key(organizationId, deviceId, batchId)),
                AgentUploadBatchDocument.class)).map(this::toDomain);
    }

    @Override
    public Claim claim(String organizationId, String deviceId, String batchId, Instant now, Instant staleBefore,
                       Instant expiresAt) {
        AgentUploadBatchDocument d = new AgentUploadBatchDocument();
        d.setOrganizationId(organizationId);
        d.setDeviceId(deviceId);
        d.setBatchId(batchId);
        d.setClaimedAt(now);
        d.setExpiresAt(expiresAt);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        try {
            mongoTemplate.insert(d);
            return new Claim.Owned(now);
        } catch (DuplicateKeyException taken) {
            // the unique key holds the batch: processed, running, or a claim left by a stopped server
        }
        Query stale = new Query(key(organizationId, deviceId, batchId).and("response").isNull()
                .and("claimedAt").lt(staleBefore));
        Update takeOver = new Update().set("claimedAt", now).set("updatedAt", now).inc("version", 0L);
        if (mongoTemplate.updateFirst(stale, takeOver, AgentUploadBatchDocument.class).getModifiedCount() > 0) {
            return new Claim.Owned(now);
        }
        return find(organizationId, deviceId, batchId)
                .<Claim>map(b -> b.getResponse() != null ? new Claim.Done(b.getResponse()) : new Claim.Running())
                .orElseGet(Claim.Running::new);
    }

    @Override
    public void complete(String organizationId, String deviceId, String batchId, UsageUploadResult response) {
        Query query = new Query(key(organizationId, deviceId, batchId).and("response").isNull());
        Update update = new Update().set("response", toDocument(response)).set("updatedAt", Instant.now())
                .inc("version", 0L);
        mongoTemplate.updateFirst(query, update, AgentUploadBatchDocument.class);
    }

    @Override
    public void release(String organizationId, String deviceId, String batchId, Instant claimedAt) {
        mongoTemplate.remove(new Query(key(organizationId, deviceId, batchId).and("response").isNull()
                .and("claimedAt").is(claimedAt)), AgentUploadBatchDocument.class);
    }

    private static AgentUploadBatchDocument.ResponseDocument toDocument(UsageUploadResult r) {
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
        return rd;
    }

    private AgentUploadBatch toDomain(AgentUploadBatchDocument d) {
        AgentUploadBatchDocument.ResponseDocument rd = d.getResponse();
        UsageUploadResult response = null;
        if (rd != null) {
            List<RowResult> rows = rd.getResults() == null ? List.of() : rd.getResults().stream()
                    .map(x -> new RowResult(x.getKey(), UsageUploadResult.Status.valueOf(x.getStatus()),
                            x.getAllocation() == null ? null : UsageUploadResult.Allocation.valueOf(x.getAllocation()),
                            x.getWorkItemId(), x.getTaskRef(), x.getReason(), x.getField(), x.getRetryable()))
                    .toList();
            response = new UsageUploadResult(rd.getBatchId(), rd.getAccepted(), rd.getDuplicate(), rd.getRejected(),
                    rows);
        }
        AgentUploadBatch b = new AgentUploadBatch(d.getOrganizationId(), d.getDeviceId(), d.getBatchId(),
                response, d.getCreatedAt(), d.getExpiresAt());
        b.setId(d.getId());
        b.setVersion(d.getVersion());
        return b;
    }
}
