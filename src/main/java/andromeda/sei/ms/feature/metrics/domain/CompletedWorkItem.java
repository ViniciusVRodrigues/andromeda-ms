package andromeda.sei.ms.feature.metrics.domain;

import java.time.Instant;
import java.util.Objects;

/** A completed work item seen through the {@link DeliveryMethod#BOARDS_PROXY} fallback. */
public record CompletedWorkItem(String id, boolean bug, Instant completedAt, Double leadTimeHours) {

    public CompletedWorkItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}
