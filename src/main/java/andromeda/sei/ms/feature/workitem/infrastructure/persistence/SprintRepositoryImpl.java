package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.SharedIterations;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SprintRepositoryImpl implements SprintRepository {

    /**
     * Twins of a shared iteration have the same dates: break the tie like
     * {@link SharedIterations#CANONICAL_ORDER} so the closest-sprint fallback lands on the twin
     * the work items are linked to.
     */
    private static final org.springframework.data.domain.Sort CANONICAL_TIE_BREAK =
            org.springframework.data.domain.Sort.by("createdAt", "teamId", "id");

    private final SprintMongoRepository mongoRepository;
    private final SprintDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public Sprint save(Sprint sprint) {
        SprintDocument doc = mapper.toDocument(sprint);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }

        Query query = Query.query(Criteria.where("organizationId").is(sprint.getOrganizationId())
                .and("externalId").is(sprint.getExternalId())
                .and("teamId").is(sprint.getTeamId()));

        Update update = new Update()
                .set("projectId", doc.getProjectId())
                .set("source", doc.getSource())
                .set("teamName", doc.getTeamName())
                .set("name", doc.getName())
                .set("path", doc.getPath())
                .set("startDate", doc.getStartDate())
                .set("endDate", doc.getEndDate())
                .set("timeFrame", doc.getTimeFrame())
                .set("active", doc.isActive())
                .set("updatedAt", now)
                .setOnInsert("organizationId", doc.getOrganizationId())
                .setOnInsert("externalId", doc.getExternalId())
                .setOnInsert("teamId", doc.getTeamId())
                .setOnInsert("createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt() : now);

        mongoTemplate.upsert(query, update, SprintDocument.class);

        return findByOrganizationIdAndExternalIdAndTeamId(
                sprint.getOrganizationId(), sprint.getExternalId(), sprint.getTeamId())
                .orElseThrow();
    }

    @Override
    public Optional<Sprint> findByOrganizationIdAndExternalIdAndTeamId(
            String organizationId, String externalId, String teamId) {
        return mongoRepository.findByOrganizationIdAndExternalIdAndTeamId(organizationId, externalId, teamId)
                .map(mapper::toDomain);
    }

    @Override
    public List<Sprint> findAllByOrganizationIdAndProjectIdAndPath(String organizationId, String projectId,
                                                                   String path) {
        return mongoRepository.findAllByOrganizationIdAndProjectIdAndPathAndActiveTrue(organizationId, projectId, path)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Sprint> findAllByOrganizationId(String organizationId) {
        return mongoRepository.findAllByOrganizationId(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Sprint> findAllByOrganizationIdAndProjectId(String organizationId, String projectId) {
        return mongoRepository.findAllByOrganizationIdAndProjectId(organizationId, projectId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Sprint> findCurrentSprintsByOrganizationId(String organizationId) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("timeFrame").is(Sprint.TimeFrame.CURRENT)
                .and("active").is(true));
        return mongoTemplate.find(query, SprintDocument.class)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Sprint> findCurrentSprintByOrganizationIdAndProjectId(String orgId, String projectId) {
        Query query = Query.query(Criteria.where("organizationId").is(orgId)
                .and("projectId").is(projectId)
                .and("timeFrame").is(Sprint.TimeFrame.CURRENT)
                .and("active").is(true));
        // findOne returned an arbitrary team's twin of a shared iteration: pick with the same
        // comparator the work item sync uses to link items, or the dashboard reads zero items.
        return SharedIterations.canonical(mongoTemplate.find(query, SprintDocument.class)
                .stream().map(mapper::toDomain).toList());
    }

    @Override
    public Optional<Sprint> findClosestSprintByOrganizationIdAndDay(String organizationId, LocalDate day) {
        Instant dayInstant = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        // Prefer the most recently ended sprint before the day (look back first)
        Query past = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("active").is(true)
                .and("endDate").lte(dayInstant))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "endDate")
                        .and(CANONICAL_TIE_BREAK))
                .limit(1);
        SprintDocument closest = mongoTemplate.findOne(past, SprintDocument.class);
        if (closest != null) return Optional.of(mapper.toDomain(closest));

        // Fallback: nearest upcoming sprint
        Query future = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("active").is(true)
                .and("startDate").gte(dayInstant))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "startDate")
                        .and(CANONICAL_TIE_BREAK))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(future, SprintDocument.class))
                .map(mapper::toDomain);
    }

    @Override
    public List<Sprint> findByOrganizationIdAndDay(String organizationId, LocalDate day) {
        Instant dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("active").is(true)
                .and("startDate").lte(dayEnd)
                .and("endDate").gte(dayStart));
        return mongoTemplate.find(query, SprintDocument.class)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Sprint> findById(String id) {
        return mongoRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Sprint> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId).map(mapper::toDomain);
    }
}
