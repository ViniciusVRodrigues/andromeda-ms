package andromeda.sei.ms.feature.dashboardlayout.domain;

import java.time.Instant;
import java.util.List;

/**
 * A user's customized "Resumo" for one view (design.md D6). At most one active layout per
 * (organization, user, viewKey); restoring the default is a soft delete.
 */
public class DashboardLayout {

    private final String id;
    private final String organizationId;
    private final String userId;
    private final DashboardViewKey viewKey;
    private List<DashboardSection> sections;
    private List<String> hidden;
    private boolean active;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public DashboardLayout(String id, String organizationId, String userId, DashboardViewKey viewKey,
                           List<DashboardSection> sections, List<String> hidden, boolean active, Long version,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.viewKey = viewKey;
        this.sections = sections == null ? List.of() : List.copyOf(sections);
        this.hidden = hidden == null ? List.of() : List.copyOf(hidden);
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new, unsaved, active layout; validates the content first. */
    public static DashboardLayout create(String organizationId, String userId, DashboardViewKey viewKey,
                                         List<DashboardSection> sections, List<String> hidden) {
        DashboardLayoutRules.validate(sections, hidden);
        return new DashboardLayout(null, organizationId, userId, viewKey, sections, hidden, true, null, null, null);
    }

    /** Replaces sections and hidden ids after validating them; identity and version are kept. */
    public void replaceContent(List<DashboardSection> newSections, List<String> newHidden) {
        DashboardLayoutRules.validate(newSections, newHidden);
        this.sections = List.copyOf(newSections);
        this.hidden = List.copyOf(newHidden);
    }

    /** Soft delete: the view falls back to the catalog default. */
    public void deactivate() {
        this.active = false;
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getUserId() { return userId; }
    public DashboardViewKey getViewKey() { return viewKey; }
    public List<DashboardSection> getSections() { return sections; }
    public List<String> getHidden() { return hidden; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
