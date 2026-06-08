package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AiUsageRepositoryImpl implements AIUsageRepository {

    private final AiUsageMongoRepository mongoRepository;
    private final AiUsageDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public void upsert(AIUsage usage) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("organizationId").is(usage.getOrganizationId())
                .and("userId").is(usage.getUserId())
                .and("datasetId").is(usage.getDatasetId())
                .and("day").is(usage.getDay())
                .and("model").is(usage.getModel())
                .and("workspaceId").is(usage.getWorkspaceId())
                .and("machineId").is(usage.getMachineId()));

        Update update = new Update()
                .set("gitHubLogin", usage.getGitHubLogin())
                .set("workspaceName", usage.getWorkspaceName())
                .set("machineName", usage.getMachineName())
                .set("inputTokens", usage.getInputTokens())
                .set("outputTokens", usage.getOutputTokens())
                .set("interactions", usage.getInteractions())
                .set("uploadedAt", usage.getUploadedAt())
                .set("active", true)
                .set("updatedAt", now)
                .setOnInsert("createdAt", now);

        mongoTemplate.upsert(query, update, AiUsageDocument.class);
    }

    @Override
    public List<AIUsage> findByOrganizationIdAndUserIdAndDayBetween(
            String organizationId, String userId, LocalDate from, LocalDate to) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDayBetween(organizationId, userId, from, to)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AIUsage> findByOrganizationIdAndDayBetween(
            String organizationId, LocalDate from, LocalDate to) {
        return mongoRepository.findByOrganizationIdAndDayBetween(organizationId, from, to)
                .stream().map(mapper::toDomain).toList();
    }
}
