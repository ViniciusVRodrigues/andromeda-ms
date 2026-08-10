package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;

public class RepoMetrics {

    private String id;
    private String organizationId;
    private String repoId;
    private Instant periodStart;
    private Instant periodEnd;
    private Double cfr;
    private boolean estimated;
    private Instant calculatedAt;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public RepoMetrics(String id, String organizationId, String repoId, Instant periodStart, Instant periodEnd,
                        Double cfr, boolean estimated, Instant calculatedAt,
                        boolean active, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.organizationId = organizationId;
        this.repoId = repoId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.cfr = cfr;
        this.estimated = estimated;
        this.calculatedAt = calculatedAt;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getRepoId() { return repoId; }
    public void setRepoId(String repoId) { this.repoId = repoId; }

    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public Double getCfr() { return cfr; }
    public void setCfr(Double cfr) { this.cfr = cfr; }

    public boolean isEstimated() { return estimated; }
    public void setEstimated(boolean estimated) { this.estimated = estimated; }

    public Instant getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(Instant calculatedAt) { this.calculatedAt = calculatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
