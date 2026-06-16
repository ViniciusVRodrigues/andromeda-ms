package andromeda.sei.ms.feature.project.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Project {

    private String id;
    private String organizationId;
    private String name;
    private String slug;
    private String description;
    private boolean active;
    private List<String> memberIds;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Project(String id, String organizationId, String name, String slug, String description,
                   boolean active, List<String> memberIds, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.active = active;
        this.memberIds = memberIds != null ? memberIds : new ArrayList<>();
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds != null ? memberIds : new ArrayList<>(); }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
