package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.shared.application.port.PermissionSnapshotCache;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Local Caffeine cache of effective-permission snapshots (project-scoped-permissions D5). A single
 * instance is enough today; with replicas the invalidation events must move to pub/sub. The TTL
 * (default 5 min) caps staleness after changes made outside the use cases.
 */
@Component
public class CaffeinePermissionSnapshotCache implements PermissionSnapshotCache {

    private static final long MAX_ENTRIES = 10_000;

    private final Cache<Key, Optional<UserPermissionSnapshot>> cache;

    public CaffeinePermissionSnapshotCache(
            @Value("${security.permissions.cache-ttl:PT5M}") Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(MAX_ENTRIES)
                .build();
    }

    @Override
    public Optional<UserPermissionSnapshot> get(String organizationId, String userId,
                                                Supplier<Optional<UserPermissionSnapshot>> loader) {
        return cache.get(new Key(organizationId, userId), k -> loader.get());
    }

    @Override
    public void evict(String organizationId, String userId) {
        cache.invalidate(new Key(organizationId, userId));
    }

    @Override
    public void evictAll() {
        cache.invalidateAll();
    }

    private record Key(String organizationId, String userId) {}
}
