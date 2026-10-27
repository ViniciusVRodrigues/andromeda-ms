package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
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
public class SprintMetricsRepositoryImpl implements SprintMetricsRepository {

    private final SprintMetricsMongoRepository mongoRepository;
    private final SprintMetricsDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public SprintMetrics save(SprintMetrics metrics) {
        Instant now = Instant.now();

        Query query = Query.query(Criteria
                .where("organizationId").is(metrics.getOrganizationId())
                .and("sprintId").is(metrics.getSprintId())
                .and("source").is(metrics.getSource()));

        Update update = new Update()
                .set("projectId", metrics.getProjectId())
                .set("velocity", metrics.getVelocity())
                .set("avgCycleTime", metrics.getAvgCycleTime())
                .set("avgLeadTime", metrics.getAvgLeadTime())
                .set("plannedEffort", metrics.getPlannedEffort())
                .set("plannedComplexity", metrics.getPlannedComplexity())
                .set("deliveredComplexity", metrics.getDeliveredComplexity())
                .set("cycleTimeP50", metrics.getCycleTimeP50())
                .set("cycleTimeP90", metrics.getCycleTimeP90())
                .set("leadTimeP50", metrics.getLeadTimeP50())
                .set("leadTimeP90", metrics.getLeadTimeP90())
                .set("completedItemCount", metrics.getCompletedItemCount())
                .set("totalItemCount", metrics.getTotalItemCount())
                .set("itemsWithPoints", metrics.getItemsWithPoints())
                .set("calculatedAt", metrics.getCalculatedAt())
                .set("active", metrics.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", metrics.getOrganizationId())
                .setOnInsert("sprintId", metrics.getSprintId())
                .setOnInsert("source", metrics.getSource())
                .setOnInsert("createdAt", now);

        mongoTemplate.upsert(query, update, SprintMetricsDocument.class);

        return findByOrganizationIdAndSprintIdAndSource(
                metrics.getOrganizationId(), metrics.getSprintId(), metrics.getSource())
                .orElseThrow();
    }

    @Override
    public Optional<SprintMetrics> findByOrganizationIdAndSprintIdAndSource(
            String organizationId, String sprintId, WorkItem.Source source) {
        return mongoRepository.findByOrganizationIdAndSprintIdAndSource(organizationId, sprintId, source)
                .map(mapper::toDomain);
    }

    @Override
    public List<SprintMetrics> findAllByOrganizationIdAndProjectId(String organizationId, String projectId) {
        return mongoRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream().map(mapper::toDomain).toList();
    }
}
