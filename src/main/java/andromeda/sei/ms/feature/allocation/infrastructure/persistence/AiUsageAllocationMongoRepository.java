package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface AiUsageAllocationMongoRepository extends MongoRepository<AiUsageAllocationDocument, String> {

    List<AiUsageAllocationDocument> findByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);

    List<AiUsageAllocationDocument> findByOrganizationIdAndUserIdAndStatusIn(
            String organizationId, String userId, List<AiUsageAllocation.Status> statuses);

    boolean existsByOrganizationIdAndUserIdAndDayAndStatus(
            String organizationId, String userId, LocalDate day, AiUsageAllocation.Status status);

    void deleteByOrganizationIdAndUserIdAndDay(
            String organizationId, String userId, LocalDate day);
}
