package andromeda.sei.ms.feature.github.infrastructure;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structural copy of {@code AzureSyncCooldownService} (design.md D7): 30s per-organization
 * cooldown for the manual GitHub sync trigger, in-memory only.
 */
@Service
public class GithubSyncCooldownService {

    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Instant> lastSyncByOrg = new ConcurrentHashMap<>();

    public long remainingCooldown(String orgId) {
        Instant last = lastSyncByOrg.get(orgId);
        if (last == null) return 0;
        long elapsed = Duration.between(last, Instant.now()).getSeconds();
        long remaining = COOLDOWN.getSeconds() - elapsed;
        return Math.max(remaining, 0);
    }

    public void record(String orgId) {
        lastSyncByOrg.put(orgId, Instant.now());
    }
}
