package andromeda.sei.ms.feature.role.domain;

import java.time.Instant;
import java.util.List;

public class Role {

    private String id;
    private String organizationId;
    private String name;
    private boolean isDefault;
    private String clonedFromRoleId;
    private List<String> permissionCodes;
    private boolean active;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Role(String id, String organizationId, String name, boolean isDefault,
                String clonedFromRoleId, List<String> permissionCodes, boolean active,
                long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.name = name;
        this.isDefault = isDefault;
        this.clonedFromRoleId = clonedFromRoleId;
        this.permissionCodes = permissionCodes;
        this.active = active;
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

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getClonedFromRoleId() { return clonedFromRoleId; }
    public void setClonedFromRoleId(String clonedFromRoleId) { this.clonedFromRoleId = clonedFromRoleId; }

    public List<String> getPermissionCodes() { return permissionCodes; }
    public void setPermissionCodes(List<String> permissionCodes) { this.permissionCodes = permissionCodes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
