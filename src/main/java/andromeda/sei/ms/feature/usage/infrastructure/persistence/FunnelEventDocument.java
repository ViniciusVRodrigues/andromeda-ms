package andromeda.sei.ms.feature.usage.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Anonymous funnel event (signup and invite, before any JWT). Declared exception to the tenant rule:
 * {@code organizationId} and {@code userId} are null until the flow ends and the events are linked to
 * the new account. Also an exception to soft delete: append-only, expired by TTL 180 days after receipt
 * ({@code createdAt}). No IP, user agent, e-mail or typed text is stored. To change the TTL later use
 * {@code collMod}: the index initializer only warns on an option conflict.
 */
@Document(collection = "funnel_events")
@CompoundIndexes({
        @CompoundIndex(def = "{'funnelId': 1, 'occurredAt': 1}", name = "ix_funnel_id_occurred")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelEventDocument {

    @Id
    private String id;

    private String funnelId;
    private String flow;
    private String stage;
    private String step;
    private Long visibleMs;
    private String value;
    private String viewport;
    private Instant occurredAt;

    /** Null until the funnel is linked. */
    private String organizationId;
    /** Null until the funnel is linked; the link filter relies on it to never re-link. */
    private String userId;
    private Instant linkedAt;

    /** Always true: the field exists for the document convention; events are never soft-deleted. */
    @Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    /** Receipt time; the TTL index counts from here. */
    @Indexed(name = "ttl_funnel_created", expireAfter = "180d")
    private Instant createdAt;

    private Instant updatedAt;
}
