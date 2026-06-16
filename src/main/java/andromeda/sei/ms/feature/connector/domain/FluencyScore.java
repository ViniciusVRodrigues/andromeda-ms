package andromeda.sei.ms.feature.connector.domain;

import java.time.Instant;
import java.util.List;

public class FluencyScore {

    private String id;
    private String organizationId;
    private String userId;
    private int overallStage;
    private String overallLabel;
    private String computedAt;
    private List<FluencyScoreCategory> categories;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public FluencyScore(String id, String organizationId, String userId,
                        int overallStage, String overallLabel, String computedAt,
                        List<FluencyScoreCategory> categories,
                        boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.overallStage = overallStage;
        this.overallLabel = overallLabel;
        this.computedAt = computedAt;
        this.categories = categories;
        this.active = active;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getOverallStage() { return overallStage; }
    public void setOverallStage(int overallStage) { this.overallStage = overallStage; }

    public String getOverallLabel() { return overallLabel; }
    public void setOverallLabel(String overallLabel) { this.overallLabel = overallLabel; }

    public String getComputedAt() { return computedAt; }
    public void setComputedAt(String computedAt) { this.computedAt = computedAt; }

    public List<FluencyScoreCategory> getCategories() { return categories; }
    public void setCategories(List<FluencyScoreCategory> categories) { this.categories = categories; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
