package andromeda.sei.ms.feature.workitem.domain;

/**
 * Test fixture: an active Azure work item with only the fields a metric test cares about; the
 * rest can be set through the setters.
 */
public final class WorkItemFixture {

    public static final String ORG = "org-1";
    public static final String PROJECT = "project-1";

    private WorkItemFixture() {}

    public static WorkItem item(String id, WorkItem.ResolvedType type, WorkItem.ResolvedStatus status) {
        return new WorkItem(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "ext-" + id, null, "Item " + id,
                type.name(), type, status.name(), status,
                null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null,
                true, 0L, null,
                null, null, null, null,
                null, null, null, null);
    }

    public static WorkItem item(String id, WorkItem.ResolvedType type, WorkItem.ResolvedStatus status,
                                Double effort, String parentExternalId) {
        WorkItem item = item(id, type, status);
        item.setEffort(effort);
        item.setParentExternalId(parentExternalId);
        return item;
    }
}
