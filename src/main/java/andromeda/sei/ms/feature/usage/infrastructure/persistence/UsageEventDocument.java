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
 * Append-only usage event. Declared exception to soft delete: rows are never edited and expire by
 * TTL 180 days after receipt ({@code createdAt}), as {@code email_verifications} and
 * {@code password_resets} do. To change the TTL later use {@code collMod}: the index initializer only
 * warns on an option conflict.
 */
@Document(collection = "usage_events")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'occurredAt': 1}", name = "ix_usage_org_occurred")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageEventDocument {

    @Id
    private String id;

    private String organizationId;
    private String userId;
    private String type;
    private String screen;
    private String viewId;
    private String sessionId;
    private Long visibleMs;
    private String element;
    private String label;
    private String area;
    private String action;
    private String value;
    private String viewport;
    private String origin;
    private Integer status;
    private String code;
    private Instant occurredAt;

    /** Always true: the field exists for the document convention; events are never soft-deleted. */
    @Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    /** Receipt time; the TTL index counts from here. */
    @Indexed(name = "ttl_usage_created", expireAfter = "180d")
    private Instant createdAt;

    private Instant updatedAt;
}
