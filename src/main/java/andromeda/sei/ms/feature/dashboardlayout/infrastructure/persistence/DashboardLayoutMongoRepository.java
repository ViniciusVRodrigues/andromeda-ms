package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** Every query carries organizationId + userId + active = true. No delete method is ever used. */
public interface DashboardLayoutMongoRepository extends MongoRepository<DashboardLayoutDocument, String> {

    Optional<DashboardLayoutDocument> findByOrganizationIdAndUserIdAndViewKeyAndActiveTrue(
            String organizationId, String userId, String viewKey);

    List<DashboardLayoutDocument> findAllByOrganizationIdAndUserIdAndActiveTrue(String organizationId, String userId);
}
