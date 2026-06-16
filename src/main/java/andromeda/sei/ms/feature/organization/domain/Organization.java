package andromeda.sei.ms.feature.organization.domain;

import andromeda.sei.ms.feature.organization.domain.exception.AiPriceDuplicateModelException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Organization {

    private String id;
    private String name;
    private String slug;
    private String sector;
    private String country;
    private List<String> seniorities;
    private List<AiPriceEntry> aiPrices;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Organization(String id, String name, String slug, String sector, String country,
                        List<String> seniorities, List<AiPriceEntry> aiPrices, boolean active,
                        Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.sector = sector;
        this.country = country;
        this.seniorities = seniorities;
        this.aiPrices = aiPrices;
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

    public List<AiPriceEntry> getAiPrices() { return aiPrices; }

    public void setAiPrices(List<AiPriceEntry> entries) {
        if (entries != null) {
            Set<String> seen = new HashSet<>();
            for (AiPriceEntry entry : entries) {
                if (!seen.add(entry.getModel())) {
                    throw new AiPriceDuplicateModelException(entry.getModel());
                }
            }
        }
        this.aiPrices = entries;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
