package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentAIUsageRepository;
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

@Repository
@RequiredArgsConstructor
public class AgentAiUsageRepositoryImpl implements AgentAIUsageRepository {

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
}
