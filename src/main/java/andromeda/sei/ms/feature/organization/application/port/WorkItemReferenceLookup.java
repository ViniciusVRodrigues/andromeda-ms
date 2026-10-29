package andromeda.sei.ms.feature.organization.application.port;

import java.util.Collection;
import java.util.Map;

/** Port of the work item lookups of the pattern detection (design 14.5). Scoped by organization. */
public interface WorkItemReferenceLookup {

    /**
     * Titles of the organization's active work items by {@code externalId}, for the given ids (one
     * batched query). Ids without an active item are absent from the map.
     */
    Map<String, String> activeTitlesByExternalId(String organizationId, Collection<String> externalIds);

    /** True when the organization has an active work item whose {@code externalId} looks like {@code PROJ-123}. */
    boolean hasJiraStyleExternalIds(String organizationId);
}
