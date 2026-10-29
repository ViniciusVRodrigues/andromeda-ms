package andromeda.sei.ms.feature.organization.domain;

import andromeda.sei.ms.feature.organization.domain.exception.AiPriceDuplicateModelException;

import java.math.BigDecimal;
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
    private BigDecimal subscriptionCostPerUserUsd;
    private boolean active;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private SyncSettings syncSettings = SyncSettings.defaults();
    private Instant lastScheduledSyncAt;
    private Instant deletedAt;
    private String deletedBy;
    private Instant purgedAt;
    private MetricSettings metricSettings = MetricSettings.defaults();
    private VcsSettings vcsSettings = VcsSettings.defaults();
    private WorkItemPatterns workItemPatterns = WorkItemPatterns.defaults();

    public Organization(String id, String name, String slug, String sector, String country,
                        List<String> seniorities, List<AiPriceEntry> aiPrices,
                        BigDecimal subscriptionCostPerUserUsd,
                        boolean active, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.sector = sector;
        this.country = country;
        this.seniorities = seniorities;
        this.aiPrices = aiPrices;
        this.subscriptionCostPerUserUsd = subscriptionCostPerUserUsd;
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

    public BigDecimal getSubscriptionCostPerUserUsd() { return subscriptionCostPerUserUsd; }
    public void setSubscriptionCostPerUserUsd(BigDecimal subscriptionCostPerUserUsd) { this.subscriptionCostPerUserUsd = subscriptionCostPerUserUsd; }

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

    /** Nunca nulo: documentos antigos sem a configuracao leem os defaults. */
    public SyncSettings getSyncSettings() { return syncSettings; }
    public void setSyncSettings(SyncSettings syncSettings) {
        this.syncSettings = syncSettings != null ? syncSettings : SyncSettings.defaults();
    }

    /** Nunca nulo: documentos sem a configuracao leem os defaults (hotfix/, BUG conta, revert conta). */
    public MetricSettings getMetricSettings() { return metricSettings; }
    public void setMetricSettings(MetricSettings metricSettings) {
        this.metricSettings = metricSettings != null ? metricSettings : MetricSettings.defaults();
    }

    /** Nunca nulo: documentos sem a configuracao leem "nunca configurado" (defaults na leitura). */
    public VcsSettings getVcsSettings() { return vcsSettings; }
    public void setVcsSettings(VcsSettings vcsSettings) {
        this.vcsSettings = vcsSettings != null ? vcsSettings : VcsSettings.defaults();
    }

    /** Nunca nulo: padrao unico de branch para work item (agente e vinculo de PR); sem configuracao le o default. */
    public WorkItemPatterns getWorkItemPatterns() { return workItemPatterns; }
    public void setWorkItemPatterns(WorkItemPatterns workItemPatterns) {
        this.workItemPatterns = workItemPatterns != null ? workItemPatterns : WorkItemPatterns.defaults();
    }

    /** Lapide: quando a exclusao foi pedida; nulo em organizacao viva. */
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    /** Lapide: userId de quem excluiu a organizacao. */
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    /** Lapide: quando o purge dos dados terminou; nulo enquanto ainda ha o que apagar. */
    public Instant getPurgedAt() { return purgedAt; }
    public void setPurgedAt(Instant purgedAt) { this.purgedAt = purgedAt; }

    /** Instante (tick do scheduler) do ultimo sync agendado disparado para a organizacao. */
    public Instant getLastScheduledSyncAt() { return lastScheduledSyncAt; }
    public void setLastScheduledSyncAt(Instant lastScheduledSyncAt) { this.lastScheduledSyncAt = lastScheduledSyncAt; }

    /**
     * O sync agendado venceu no {@code tick}? Nunca para {@link ScheduledSyncFrequency#OFF};
     * caso contrario, quando nunca rodou ou quando {@code lastScheduledSyncAt + intervalo <= tick}.
     */
    public boolean isScheduledSyncDue(Instant tick) {
        return syncSettings.scheduledSync().interval()
                .map(interval -> lastScheduledSyncAt == null || !lastScheduledSyncAt.plus(interval).isAfter(tick))
                .orElse(false);
    }
}
