package andromeda.sei.ms.feature.organization.domain;

import java.time.Instant;
import java.util.List;

public class Organization {

    private String id;
    private String name;
    private String slug;
    private String sector;
    private String country;
    private List<String> seniorities;
    private boolean active;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Organization(String id, String name, String slug, String sector, String country,
                        List<String> seniorities, boolean active,
                        long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.sector = sector;
        this.country = country;
        this.seniorities = seniorities;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public List<String> getSeniorities() { return seniorities; }
    public void setSeniorities(List<String> seniorities) { this.seniorities = seniorities; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
