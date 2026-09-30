package andromeda.sei.ms.shared.application.port;

import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache of effective-permission snapshots keyed by (organization, user)
 * (project-scoped-permissions D5). Implementations bound the staleness with a TTL, as a safety net
 * for changes made outside the use cases (manual Mongo scripts).
 */
public interface PermissionSnapshotCache {

    Optional<UserPermissionSnapshot> get(String organizationId, String userId,
                                         Supplier<Optional<UserPermissionSnapshot>> loader);

    void evict(String organizationId, String userId);

    void evictAll();
}
