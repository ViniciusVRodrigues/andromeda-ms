package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentAIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class AgentAiUsageRepositoryImpl implements AgentAIUsageRepository, AgentUsageQueries {

    private final MongoTemplate mongoTemplate;
    private final AiUsageDocumentMapper mapper;

    @Override
    public Map<String, AIUsage> findByKeys(String organizationId, String deviceId, Collection<String> keys) {
        Map<String, AIUsage> byKey = new HashMap<>();
        if (keys == null || keys.isEmpty()) return byKey;
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("deviceId").is(deviceId).and("agentKey").in(keys));
        mongoTemplate.find(query, AiUsageDocument.class)
                .forEach(d -> byKey.put(d.getAgentKey(), mapper.toDomain(d)));
        return byKey;
    }

    @Override
    public Optional<AIUsage> insert(AIUsage usage) {
        try {
            AiUsageDocument saved = mongoTemplate.insert(mapper.toDocument(usage));
            return Optional.of(mapper.toDomain(saved));
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void raiseToMax(String organizationId, String id, AIUsage values) {
        Instant now = Instant.now();
        Update update = new Update()
                .max("inputTokens", values.getInputTokens())
                .max("outputTokens", values.getOutputTokens())
                .max("cacheReadTokens", values.getCacheReadTokens())
                .max("cacheWriteTokens", values.getCacheWriteTokens())
                .max("thinkingTokens", values.getThinkingTokens())
                .max("interactions", values.getInteractions())
                .set("costUsd", values.getCostUsd())
                .set("uploadedAt", now)
                .set("updatedAt", now);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id).and("organizationId").is(organizationId)),
                update, AiUsageDocument.class);
    }

    @Override
    public List<AIUsage> findCountersByDeviceAndDayBetween(String organizationId, String deviceId,
                                                           LocalDate from, LocalDate to) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("deviceId").is(deviceId).and("agentKey").exists(true)
                .and("day").gte(from).lte(to));
        query.fields().include("day", "agentKey", "inputTokens", "outputTokens", "cacheReadTokens",
                "cacheWriteTokens", "thinkingTokens");
        return mongoTemplate.find(query, AiUsageDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<String> findKeysByDeviceAndDay(String organizationId, String deviceId, LocalDate day, int limit) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("deviceId").is(deviceId).and("agentKey").exists(true).and("day").is(day))
                .with(Sort.by(Sort.Direction.ASC, "agentKey")).limit(limit);
        query.fields().include("agentKey");
        return mongoTemplate.find(query, AiUsageDocument.class).stream().map(AiUsageDocument::getAgentKey).toList();
    }

    private static Criteria aief(String organizationId, String userId, LocalDate day, Collection<String> editors) {
        return Criteria.where("organizationId").is(organizationId).and("userId").is(userId)
                .and("day").is(day).and("source").is(null).and("editor").in(editors);
    }

    @Override
    public boolean existsActiveAief(String organizationId, String userId, LocalDate day, Collection<String> editors) {
        if (editors == null || editors.isEmpty()) return false;
        return mongoTemplate.exists(Query.query(aief(organizationId, userId, day, editors).and("active").is(true)),
                AiUsageDocument.class);
    }

    @Override
    public boolean existsAiefInValidatedAllocation(String organizationId, String userId, LocalDate day,
                                                   Collection<String> editors) {
        if (editors == null || editors.isEmpty()) return false;
        Query rows = Query.query(aief(organizationId, userId, day, editors).and("active").is(true)
                .and("allocationId").ne(null));
        List<String> allocationIds = mongoTemplate.find(rows, AiUsageDocument.class).stream()
                .map(AiUsageDocument::getAllocationId).distinct().toList();
        if (allocationIds.isEmpty()) return false;
        Query validated = Query.query(Criteria.where("_id").in(allocationIds).and("organizationId").is(organizationId)
                .and("active").is(true).and("status").is(AiUsageAllocation.Status.VALIDATED));
        return mongoTemplate.exists(validated, AiUsageAllocationDocument.class);
    }

    @Override
    public long supersedeAief(String organizationId, String userId, LocalDate day, Collection<String> editors) {
        if (editors == null || editors.isEmpty()) return 0;
        Query query = Query.query(aief(organizationId, userId, day, editors).and("active").is(true)
                .and("allocationId").is(null));
        Update update = new Update().set("active", false).set("supersededBy", "AGENT").set("updatedAt", Instant.now());
        return mongoTemplate.updateMulti(query, update, AiUsageDocument.class).getModifiedCount();
    }

    @Override
    public boolean existsActiveAgentRow(String organizationId, String userId, LocalDate day, String tool) {
        if (tool == null) return false;
        return mongoTemplate.exists(Query.query(Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId).and("day").is(day).and("source").is("AGENT").and("active").is(true)
                .and("tool").is(tool)), AiUsageDocument.class);
    }

    @Override
    public Set<String> agentModelsOfDay(String organizationId, String userId, LocalDate day) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId).and("userId").is(userId)
                .and("day").is(day).and("source").is("AGENT").and("active").is(true));
        query.fields().include("model");
        return mongoTemplate.find(query, AiUsageDocument.class).stream().map(AiUsageDocument::getModel)
                .filter(m -> m != null).collect(Collectors.toSet());
    }

    @Override
    public List<AIUsage> findUnallocatedAgentRows(String organizationId, String userId, LocalDate day) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId).and("userId").is(userId)
                .and("day").is(day).and("source").is("AGENT").and("active").is(true).and("allocationId").is(null));
        return mongoTemplate.find(query, AiUsageDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AIUsage> findActiveAgentRowsSince(String organizationId, String userId, Instant since) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId).and("userId").is(userId)
                .and("source").is("AGENT").and("active").is(true).and("bucketStart").gte(since));
        return mongoTemplate.find(query, AiUsageDocument.class).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void stampTask(String organizationId, Collection<String> ids, String workItemId, String projectId,
                          String taskRef, String attributionSource) {
        if (ids == null || ids.isEmpty()) return;
        Query query = Query.query(Criteria.where("_id").in(ids).and("organizationId").is(organizationId));
        Update update = new Update().set("workItemId", workItemId).set("projectId", projectId)
                .set("taskRef", taskRef).set("attributionSource", attributionSource).set("updatedAt", Instant.now());
        mongoTemplate.updateMulti(query, update, AiUsageDocument.class);
    }
}
