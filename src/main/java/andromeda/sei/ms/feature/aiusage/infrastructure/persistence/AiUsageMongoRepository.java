package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface AiUsageMongoRepository extends MongoRepository<AiUsageDocument, String> {

    List<AiUsageDocument> findByOrganizationIdAndUserIdAndDayInAndActiveTrue(
            String organizationId, String userId, List<LocalDate> days);

    List<AiUsageDocument> findByOrganizationIdAndUserIdAndDayAndAllocationIdIsNullAndActiveTrue(
            String organizationId, String userId, LocalDate day);

    List<AiUsageDocument> findByOrganizationIdAndUserIdAndAllocationIdIsNullAndActiveTrue(
            String organizationId, String userId);

    List<AiUsageDocument> findByOrganizationIdAndUserIdAndDayBetweenAndActiveTrue(
            String organizationId, String userId, LocalDate from, LocalDate to);

    List<AiUsageDocument> findByOrganizationIdAndActiveTrue(String organizationId);
}
