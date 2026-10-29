package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AiUsageAllocationMongoRepository extends MongoRepository<AiUsageAllocationDocument, String> {

    List<AiUsageAllocationDocument> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    List<AiUsageAllocationDocument> findByOrganizationIdAndUserIdAndStatusIn(
            String organizationId, String userId, List<AiUsageAllocation.Status> statuses);

    /** A validated day for the per-day flows: AGENT allocations do not count (agente-endpoint-v2). */
    boolean existsByOrganizationIdAndUserIdAndDayAndStatusAndAssignedByNot(
            String organizationId, String userId, LocalDate day, AiUsageAllocation.Status status,
            AiUsageAllocation.AssignedBy assignedBy);

    /** Deletes the day's allocations except those of the given {@code assignedBy} (documents without it match). */
    void deleteByOrganizationIdAndUserIdAndDayAndAssignedByNot(
            String organizationId, String userId, LocalDate day, AiUsageAllocation.AssignedBy assignedBy);

    List<AiUsageAllocationDocument> findByOrganizationIdAndUserIdAndStatus(
            String organizationId, String userId, AiUsageAllocation.Status status);

    Optional<AiUsageAllocationDocument> findByIdAndOrganizationIdAndActiveTrue(
            String id, String organizationId);
}
