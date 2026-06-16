package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AiUsageAllocationRepositoryImpl implements AiUsageAllocationRepository {

    private final AiUsageAllocationMongoRepository mongoRepository;
    private final AiUsageAllocationDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public List<AiUsageAllocation> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findPendingByOrganizationIdAndUserId(
            String organizationId, String userId) {
        return mongoRepository.findByOrganizationIdAndUserIdAndStatusIn(
                        organizationId, userId,
                        List.of(AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.Status.UNALLOCATED))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsValidatedByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.existsByOrganizationIdAndUserIdAndDayAndStatus(
                organizationId, userId, day, AiUsageAllocation.Status.VALIDATED);
    }

    @Override
    public void saveAll(List<AiUsageAllocation> allocations) {
        mongoRepository.saveAll(allocations.stream().map(mapper::toDocument).toList());
    }

    @Override
    public AiUsageAllocation save(AiUsageAllocation allocation) {
        AiUsageAllocationDocument saved = mongoRepository.save(mapper.toDocument(allocation));
        return mapper.toDomain(saved);
    }

    @Override
    public void deleteByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day) {
        mongoRepository.deleteByOrganizationIdAndUserIdAndDay(organizationId, userId, day);
    }

    @Override
    public void deleteById(String id) {
        mongoRepository.deleteById(id);
    }

    @Override
    public List<AiUsageAllocation> findByOrgUserAndStatus(
            String organizationId, String userId, AiUsageAllocation.Status status) {
        return mongoRepository.findByOrganizationIdAndUserIdAndStatus(organizationId, userId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findByOrgUserAndDay(
            String organizationId, String userId, LocalDate day) {
        return mongoRepository.findByOrganizationIdAndUserIdAndDay(organizationId, userId, day)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AiUsageAllocation> findByWorkItemId(String workItemId) {
        return mongoRepository.findBySplitsWorkItemId(workItemId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void updateAllocationIdOnUsageRecords(List<String> aiUsageIds, String allocationId) {
        if (aiUsageIds == null || aiUsageIds.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").in(aiUsageIds));
        Update update = new Update()
                .set("allocationId", allocationId)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateMulti(query, update, AiUsageDocument.class);
    }

    @Override
    public Optional<AiUsageAllocation> findById(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                .map(mapper::toDomain);
    }

    @Override
    public List<AiUsageAllocation> findValidatedByOrgUserAndWorkItemId(
            String organizationId, String userId, String workItemId) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true)
                .and("splits.workItemId").is(workItemId);
        Query query = Query.query(criteria);
        return mongoTemplate.find(query, AiUsageAllocationDocument.class)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Page<AiUsageAllocation> findValidatedByOrgUserAndOptionalMonth(
            String organizationId, String userId, YearMonth month, Pageable pageable) {
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("status").is(AiUsageAllocation.Status.VALIDATED)
                .and("active").is(true);

        if (month != null) {
            LocalDate from = month.atDay(1);
            LocalDate to = month.atEndOfMonth();
            criteria = criteria.and("day").gte(from).lte(to);
        }

        Query countQuery = Query.query(criteria);
        long total = mongoTemplate.count(countQuery, AiUsageAllocationDocument.class);

        Query dataQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "day"))
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize());

        List<AiUsageAllocation> results = mongoTemplate
                .find(dataQuery, AiUsageAllocationDocument.class)
                .stream().map(mapper::toDomain).toList();

        return new PageImpl<>(results, pageable, total);
    }
}