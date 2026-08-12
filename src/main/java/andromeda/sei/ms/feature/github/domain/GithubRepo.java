package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class GithubRepo {

    private String id;
    private String organizationId;
    private String externalId;
    private String owner;
    private String name;
    private String fullName;
    private Instant externalUpdatedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public GithubRepo(String id, String organizationId, String externalId, String owner, String name,
                       String fullName, Instant externalUpdatedAt,
                       boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.externalId = externalId;
        this.owner = owner;
        this.name = name;
        this.fullName = fullName;
        this.externalUpdatedAt = externalUpdatedAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Instant getExternalUpdatedAt() { return externalUpdatedAt; }
    public void setExternalUpdatedAt(Instant externalUpdatedAt) { this.externalUpdatedAt = externalUpdatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
