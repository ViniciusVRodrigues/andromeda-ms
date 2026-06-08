package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface AiUsageMongoRepository extends MongoRepository<AiUsageDocument, String> {

    List<AiUsageDocument> findByOrganizationIdAndUserIdAndDayBetween(
            String organizationId, String userId, LocalDate from, LocalDate to);

    List<AiUsageDocument> findByOrganizationIdAndDayBetween(
            String organizationId, LocalDate from, LocalDate to);
}
