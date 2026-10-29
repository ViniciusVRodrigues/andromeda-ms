package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import lombok.RequiredArgsConstructor;
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
public class AgentDeviceRepositoryImpl implements AgentDeviceRepository {

    private final AgentDeviceMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;
    private final AgentDocumentMapper mapper;

    @Override
    public AgentDevice save(AgentDevice device) {
        return mapper.toDomain(mongoRepository.save(mapper.toDocument(device)));
    }

    @Override
    public boolean existsByOrganizationId(String organizationId) {
        return mongoTemplate.exists(Query.query(Criteria.where("organizationId").is(organizationId)),
                AgentDeviceDocument.class);
    }

    @Override
    public Optional<AgentDevice> findActiveByIdAndOrganizationId(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId).map(mapper::toDomain);
    }

    @Override
    public Optional<AgentDevice> findByIdAndOrganizationId(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationId(id, organizationId).map(mapper::toDomain);
    }

    @Override
    public Optional<AgentDevice> findByAccessTokenHash(String accessTokenHash) {
        return mongoRepository.findByAccessTokenHash(accessTokenHash).map(mapper::toDomain);
    }

    @Override
    public Optional<AgentDevice> findByRefreshTokenHash(String refreshTokenHash) {
        return mongoRepository.findByRefreshTokenHash(refreshTokenHash).map(mapper::toDomain);
    }

    @Override
    public Optional<AgentDevice> findByPreviousRefreshHash(String previousRefreshHash) {
        return mongoRepository.findByPreviousRefreshHash(previousRefreshHash).map(mapper::toDomain);
    }

    @Override
    public List<AgentDevice> findDevicesOfUser(String organizationId, String userId) {
        return mongoRepository.findAllByOrganizationIdAndUserId(organizationId, userId).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public void touchSeen(String deviceId, String organizationId, Instant seenAt, String agentVersion) {
        Update update = new Update().set("lastSeenAt", seenAt);
        if (agentVersion != null) {
            update.set("agentVersion", agentVersion);
        }
        updateActive(deviceId, organizationId, update);
    }

    @Override
    public void recordHeartbeat(String deviceId, String organizationId, Instant seenAt, String agentVersion,
                                Integer pendingRows, String timezone) {
        Update update = new Update().set("lastSeenAt", seenAt);
        if (agentVersion != null) {
            update.set("agentVersion", agentVersion);
        }
        if (pendingRows != null) {
            update.set("pendingRows", pendingRows);
        }
        if (timezone != null) {
            update.set("timezone", timezone);
        }
        updateActive(deviceId, organizationId, update);
    }

    @Override
    public void updateTimezone(String deviceId, String organizationId, String timezone) {
        updateActive(deviceId, organizationId, new Update().set("timezone", timezone));
    }

    @Override
    public void touchUpload(String deviceId, String organizationId, Instant uploadAt) {
        updateActive(deviceId, organizationId, new Update().set("lastUploadAt", uploadAt));
    }

    @Override
    public boolean requestSync(String deviceId, String organizationId, Instant at, String by) {
        Query query = Query.query(Criteria.where("_id").is(deviceId)
                .and("organizationId").is(organizationId).and("active").is(true)
                .and("syncRequestedAt").isNull());
        Update update = new Update().set("syncRequestedAt", at).set("syncRequestedBy", by).inc("version", 0L);
        return mongoTemplate.updateFirst(query, update, AgentDeviceDocument.class).getModifiedCount() > 0;
    }

    @Override
    public void clearServedSync(String deviceId, String organizationId, Instant requestedAt, Instant servedAt) {
        Query query = Query.query(Criteria.where("_id").is(deviceId)
                .and("organizationId").is(organizationId).and("active").is(true)
                .and("syncRequestedAt").is(requestedAt));
        Update update = new Update().unset("syncRequestedAt").unset("syncRequestedBy")
                .set("syncServedAt", servedAt).inc("version", 0L);
        mongoTemplate.updateFirst(query, update, AgentDeviceDocument.class);
    }

    @Override
    public void setActiveTask(String deviceId, String organizationId, AgentDevice.ActiveTask task) {
        Update update = new Update();
        if (task == null) {
            update.unset("activeTask");
        } else {
            update.set("activeTask", new AgentDeviceDocument.ActiveTaskDocument(task.workItemId(), task.taskRef(),
                    task.title(), task.since(), task.repository(), task.day()));
        }
        updateActive(deviceId, organizationId, update);
    }

    private void updateActive(String deviceId, String organizationId, Update update) {
        Query query = Query.query(Criteria.where("_id").is(deviceId)
                .and("organizationId").is(organizationId).and("active").is(true));
        // Spring Data bumps @Version on any update of a versioned entity unless the update already
        // touches it: inc 0 keeps these frequent writes from failing a concurrent optimistic-lock save.
        update.inc("version", 0L);
        mongoTemplate.updateFirst(query, update, AgentDeviceDocument.class);
    }
}
