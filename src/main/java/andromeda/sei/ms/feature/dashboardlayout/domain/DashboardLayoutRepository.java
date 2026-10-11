package andromeda.sei.ms.feature.dashboardlayout.domain;

import java.util.List;
import java.util.Optional;

/** Port for {@code dashboard_layouts}. Every lookup is scoped by organization, user and {@code active}. */
public interface DashboardLayoutRepository {

    Optional<DashboardLayout> findActive(String organizationId, String userId, DashboardViewKey viewKey);

    List<DashboardLayout> findAllActive(String organizationId, String userId);

    /**
     * Inserts or updates with optimistic locking. A stale version, or a second active layout for the
     * same (organization, user, viewKey), raises {@code DashboardLayoutVersionConflictException}.
     */
    DashboardLayout save(DashboardLayout layout);
}
