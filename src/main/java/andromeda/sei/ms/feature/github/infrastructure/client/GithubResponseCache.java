package andromeda.sei.ms.feature.github.infrastructure.client;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory conditional-request cache keyed by full request URL (including query params).
 * Stores the last known ETag/Last-Modified and response body for each URL so repeat polls can
 * send {@code If-None-Match}/{@code If-Modified-Since} and, on a 304, reuse the cached body
 * without spending primary rate-limit budget (design.md D4).
 *
 * <p>In-memory rather than Mongo-backed by deliberate design choice: it's a pure performance
 * optimization (worst case on a cache miss is just a normal full request), so losing the cache
 * on restart has no correctness impact and avoids introducing a new persistence dependency.
 */
@Component
public class GithubResponseCache {

    public record CacheEntry(String etag, String lastModified, List<?> body) {}

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CacheEntry get(String url) {
        return cache.get(url);
    }

    public void put(String url, String etag, String lastModified, List<?> body) {
        cache.put(url, new CacheEntry(etag, lastModified, body));
    }
}
