package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutVersionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adapter for {@code dashboard_layouts}. The two races the use case cannot see are translated to
 * 409: a stale {@code @Version} on update, and the partial unique index rejecting a second active
 * layout on create.
 */
@Repository
@RequiredArgsConstructor
public class DashboardLayoutRepositoryImpl implements DashboardLayoutRepository {

    private final DashboardLayoutMongoRepository mongoRepository;
    private final DashboardLayoutDocumentMapper mapper;

    @Override
    public Optional<DashboardLayout> findActive(String organizationId, String userId, DashboardViewKey viewKey) {
        return mongoRepository
                .findByOrganizationIdAndUserIdAndViewKeyAndActiveTrue(organizationId, userId, viewKey.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<DashboardLayout> findAllActive(String organizationId, String userId) {
        return mongoRepository.findAllByOrganizationIdAndUserIdAndActiveTrue(organizationId, userId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public DashboardLayout save(DashboardLayout layout) {
        DashboardLayoutDocument doc = mapper.toDocument(layout);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        try {
            return mapper.toDomain(mongoRepository.save(doc));
        } catch (DuplicateKeyException | OptimisticLockingFailureException e) {
            throw new DashboardLayoutVersionConflictException(layout.getViewKey().value());
        }
    }
}
