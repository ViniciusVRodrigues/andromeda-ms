package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageSummary;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageSummaryByModel;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

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
                .setOnInsert("createdAt", now)
                .setOnInsert("allocationId", null);

        mongoTemplate.upsert(query, update, AiUsageDocument.class);
    }

    @Override
    public void updateCost(String organizationId, String userId, String datasetId, LocalDate day,
                           String model, String workspaceId, String machineId, BigDecimal costUsd) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("datasetId").is(datasetId)
                .and("day").is(day)
                .and("model").is(model)
                .and("workspaceId").is(workspaceId)
                .and("machineId").is(machineId));

        Update update = new Update()
                .set("costUsd", costUsd)
                .set("updatedAt", Instant.now());

        mongoTemplate.updateFirst(query, update, AiUsageDocument.class);
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

    @Override
    public List<AIUsage> findByOrganizationIdAndUserIdAndDayIn(
            String organizationId, String userId, List<LocalDate> days) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDayInAndActiveTrue(organizationId, userId, days)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public AiUsageSummary findSummaryByUserIdAndFilters(
            String organizationId, String userId, LocalDate startDate, LocalDate endDate, String model) {
        MatchOperation match = Aggregation.match(buildUserFilterCriteria(organizationId, userId, startDate, endDate, model));
        GroupOperation group = Aggregation.group()
                .sum("inputTokens").as("totalInputTokens")
                .sum("outputTokens").as("totalOutputTokens")
                .sum("interactions").as("totalInteractions")
                .sum("costUsd").as("totalCostUsd");

        Aggregation agg = Aggregation.newAggregation(match, group);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, AiUsageDocument.class, Map.class);

        Map result = results.getUniqueMappedResult();
        if (result == null) {
            return new AiUsageSummary(0L, 0L, 0L, BigDecimal.ZERO);
        }

        return new AiUsageSummary(
                toLong(result.get("totalInputTokens")),
                toLong(result.get("totalOutputTokens")),
                toLong(result.get("totalInteractions")),
                toBigDecimal(result.get("totalCostUsd"))
        );
    }

    @Override
    public List<AiUsageSummaryByModel> findSummaryGroupedByModel(
            String organizationId, String userId, LocalDate startDate, LocalDate endDate, String model) {
        MatchOperation match = Aggregation.match(buildUserFilterCriteria(organizationId, userId, startDate, endDate, model));
        GroupOperation group = Aggregation.group("model")
                .sum("inputTokens").as("totalInputTokens")
                .sum("outputTokens").as("totalOutputTokens")
                .sum("interactions").as("totalInteractions")
                .sum("costUsd").as("totalCostUsd");
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalInputTokens"));

        Aggregation agg = Aggregation.newAggregation(match, group, sort);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, AiUsageDocument.class, Map.class);

        return results.getMappedResults().stream()
                .map(r -> new AiUsageSummaryByModel(
                        (String) r.get("_id"),
                        toLong(r.get("totalInputTokens")),
                        toLong(r.get("totalOutputTokens")),
                        toLong(r.get("totalInteractions")),
                        toBigDecimal(r.get("totalCostUsd"))
                ))
                .toList();
    }

    @Override
    public List<DailyAiUsage> findDailyUsageByUserIdAndDateRange(
            String organizationId, String userId, LocalDate from, LocalDate to, String model) {
        MatchOperation match = Aggregation.match(buildUserFilterCriteria(organizationId, userId, from, to, model));
        GroupOperation group = Aggregation.group("day")
                .sum("inputTokens").as("inputTokens")
                .sum("outputTokens").as("outputTokens")
                .sum("interactions").as("interactions")
                .sum("costUsd").as("costUsd");
        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.ASC, "_id"));

        Aggregation agg = Aggregation.newAggregation(match, group, sort);
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, AiUsageDocument.class, Map.class);

        return results.getMappedResults().stream()
                .map(r -> new DailyAiUsage(
                        toLocalDate(r.get("_id")),
                        toLong(r.get("inputTokens")),
                        toLong(r.get("outputTokens")),
                        toLong(r.get("interactions")),
                        toBigDecimal(r.get("costUsd"))
                ))
                .toList();
    }

    @Override
    public List<AIUsage> findUnallocatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDayAndAllocationIdIsNull(
                        organizationId, userId, day)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AIUsage> findUnallocatedByOrganizationIdAndUserId(
            String organizationId, String userId) {
        return mongoRepository.findByOrganizationIdAndUserIdAndAllocationIdIsNullAndActiveTrue(
                        organizationId, userId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AIUsage> findByOrganizationIdAndUserIdAndMonth(
            String organizationId, String userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return mongoRepository.findByOrganizationIdAndUserIdAndDayBetweenAndActiveTrue(
                        organizationId, userId, from, to)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Map<String, BigDecimal> findTotalCostByUserIdsAndDateRange(
            String organizationId, List<String> userIds, LocalDate from, LocalDate to) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Map.of();
        }
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").in(userIds)
                .and("active").is(true)
                .and("day").gte(from).lte(to);

        MatchOperation match = Aggregation.match(criteria);
        GroupOperation group = Aggregation.group("userId").sum("costUsd").as("totalCost");
        Aggregation agg = Aggregation.newAggregation(match, group);

        AggregationResults<Map> results = mongoTemplate.aggregate(agg, AiUsageDocument.class, Map.class);

        java.util.Map<String, BigDecimal> resultMap = new java.util.HashMap<>();
        for (Map r : results.getMappedResults()) {
            String uid = (String) r.get("_id");
            BigDecimal cost = toBigDecimal(r.get("totalCost"));
            if (uid != null) {
                resultMap.put(uid, cost != null ? cost : BigDecimal.ZERO);
            }
        }
        return resultMap;
    }

    private Criteria buildUserFilterCriteria(String organizationId, String userId,
                                              LocalDate startDate, LocalDate endDate, String model) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("active").is(true);
        if (startDate != null && endDate != null) {
            criteria = criteria.and("day").gte(startDate).lte(endDate);
        } else if (startDate != null) {
            criteria = criteria.and("day").gte(startDate);
        } else if (endDate != null) {
            criteria = criteria.and("day").lte(endDate);
        }
        if (model != null && !model.isBlank()) {
            criteria = criteria.and("model").is(model);
        }
        return criteria;
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        return null;
    }
}
