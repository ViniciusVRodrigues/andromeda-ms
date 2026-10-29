package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.MetricSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrganizationDocumentMapper {

    public Organization toDomain(OrganizationDocument doc) {
        List<AiPriceEntry> aiPrices = doc.getAiPrices() == null ? null :
                doc.getAiPrices().stream()
                        .map(e -> new AiPriceEntry(e.getModel(), e.getPricePerKInput(), e.getPricePerKOutput()))
                        .toList();

        Organization organization = new Organization(
                doc.getId(),
                doc.getName(),
                doc.getSlug(),
                doc.getSector(),
                doc.getCountry(),
                doc.getSeniorities(),
                aiPrices,
                doc.getSubscriptionCostPerUserUsd(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
        organization.setSyncSettings(toSyncSettings(doc.getSyncSettings()));
        organization.setLastScheduledSyncAt(doc.getLastScheduledSyncAt());
        organization.setDeletedAt(doc.getDeletedAt());
        organization.setDeletedBy(doc.getDeletedBy());
        organization.setPurgedAt(doc.getPurgedAt());
        organization.setMetricSettings(toMetricSettings(doc.getMetricSettings()));
        organization.setVcsSettings(toVcsSettings(doc.getVcsSettings()));
        organization.setWorkItemPatterns(toWorkItemPatterns(doc));
        return organization;
    }

    /**
     * Stored patterns, or (design 14.3) the legacy {@code vcsSettings.workItemLink} read as patterns,
     * or the default. No migration job: the first save writes {@code workItemPatterns}.
     */
    private WorkItemPatterns toWorkItemPatterns(OrganizationDocument doc) {
        WorkItemPatternsDocument stored = doc.getWorkItemPatterns();
        if (stored == null) {
            VcsSettingsDocument.WorkItemLinkDocument link = doc.getVcsSettings() == null ? null
                    : doc.getVcsSettings().getWorkItemLink();
            return link == null ? WorkItemPatterns.defaults()
                    : WorkItemPatterns.fromLegacy(new WorkItemLinkConfig(link.isEnabled(), link.getPrefixes()));
        }
        WorkItemPatterns.Mode mode;
        try {
            mode = stored.getMode() == null ? WorkItemPatterns.Mode.DEFAULT : WorkItemPatterns.Mode.valueOf(stored.getMode());
        } catch (IllegalArgumentException e) {
            mode = WorkItemPatterns.Mode.CUSTOM;
        }
        List<WorkItemPatterns.Entry> entries = stored.getPatterns() == null ? null : stored.getPatterns().stream()
                .map(e -> WorkItemPatterns.Entry.of(e.getRegex(), e.isAlsoInText())).toList();
        WorkItemPatternsDocument.DetectionDocument d = stored.getDetection();
        WorkItemPatterns.Detection detection = d == null ? null : new WorkItemPatterns.Detection(
                d.getRanAt(), d.getBranchesScanned(), d.getCoverage(), d.isApplied(),
                d.getCandidates() == null ? null : d.getCandidates().stream().map(c -> new WorkItemPatterns.Candidate(
                        c.getRegex(), c.getPreset(), c.getCoverage(), c.getPrecision(), c.getMatchedBranches(),
                        c.getResolvedBranches(), c.getExamples() == null ? null : c.getExamples().stream()
                                .map(x -> new WorkItemPatterns.Example(x.getBranch(), x.getWorkItemNumber(), x.getTitle()))
                                .toList())).toList());
        return new WorkItemPatterns(stored.isEnabled(), mode, entries, stored.getConfirmedBy(), stored.getConfirmedAt(),
                stored.getUpdatedBy(), stored.getUpdatedAt(), detection);
    }

    /** Null for "never configured": keeps the legacy fallback reachable and avoids freezing the defaults. */
    private WorkItemPatternsDocument toWorkItemPatternsDocument(WorkItemPatterns p) {
        if (p == null || p.isUntouchedDefault()) return null;
        WorkItemPatternsDocument.DetectionDocument detection = p.detection() == null ? null
                : new WorkItemPatternsDocument.DetectionDocument(p.detection().ranAt(), p.detection().branchesScanned(),
                p.detection().coverage(), p.detection().applied(), p.detection().candidates().stream()
                .map(c -> new WorkItemPatternsDocument.CandidateDocument(c.regex(), c.preset(), c.coverage(),
                        c.precision(), c.matchedBranches(), c.resolvedBranches(), c.examples().stream()
                        .map(x -> new WorkItemPatternsDocument.ExampleDocument(x.branch(), x.workItemNumber(), x.title()))
                        .toList())).toList());
        return new WorkItemPatternsDocument(p.enabled(), p.mode().name(), p.patterns().stream()
                .map(e -> new WorkItemPatternsDocument.EntryDocument(e.regex(), e.preset(), e.alsoInText())).toList(),
                p.confirmedBy(), p.confirmedAt(), p.updatedBy(), p.updatedAt(), detection);
    }

    /** Sem subdocumento (ou sem workItemLink): "nunca configurado", o dominio le os defaults. */
    private VcsSettings toVcsSettings(VcsSettingsDocument doc) {
        if (doc == null) return VcsSettings.defaults();
        VcsSettingsDocument.WorkItemLinkDocument link = doc.getWorkItemLink();
        WorkItemLinkConfig config = link == null ? null : new WorkItemLinkConfig(link.isEnabled(), link.getPrefixes());
        return new VcsSettings(config, doc.getServiceIdentities());
    }

    /** Grava {@code null} para "nunca configurado", preservando a distincao na volta. */
    private VcsSettingsDocument toVcsSettingsDocument(VcsSettings settings) {
        if (settings == null) return null;
        WorkItemLinkConfig link = settings.workItemLink();
        List<String> identities = settings.serviceIdentities().isEmpty() ? null : settings.serviceIdentities();
        if (link == null && identities == null) return null;
        VcsSettingsDocument.WorkItemLinkDocument linkDoc = link == null ? null
                : new VcsSettingsDocument.WorkItemLinkDocument(link.isEnabled(), link.getPrefixes());
        return new VcsSettingsDocument(linkDoc, identities);
    }

    /** Defaults na leitura, campo a campo: documento antigo (ou parcial) le hotfix/, BUG e revert. */
    private MetricSettings toMetricSettings(MetricSettingsDocument doc) {
        if (doc == null || doc.getRemediationRules() == null) return MetricSettings.defaults();
        RemediationRulesDocument rules = doc.getRemediationRules();
        RemediationRules defaults = RemediationRules.defaults();
        return new MetricSettings(new RemediationRules(
                rules.getBranchPrefixes() != null ? rules.getBranchPrefixes() : defaults.branchPrefixes(),
                rules.getBugLinkedCountsAsRemediation() != null
                        ? rules.getBugLinkedCountsAsRemediation() : defaults.bugLinkedCountsAsRemediation(),
                rules.getRevertCountsAsRemediation() != null
                        ? rules.getRevertCountsAsRemediation() : defaults.revertCountsAsRemediation()));
    }

    private MetricSettingsDocument toMetricSettingsDocument(MetricSettings settings) {
        RemediationRules rules = settings.remediationRules();
        return new MetricSettingsDocument(new RemediationRulesDocument(
                rules.branchPrefixes(), rules.bugLinkedCountsAsRemediation(), rules.revertCountsAsRemediation()));
    }

    /** Defaults na leitura: documento sem configuracao (ou com campo faltando) le OFF/90. */
    private SyncSettings toSyncSettings(SyncSettingsDocument doc) {
        if (doc == null) return SyncSettings.defaults();
        SyncSettings defaults = SyncSettings.defaults();
        return new SyncSettings(
                doc.getScheduledSync() != null ? doc.getScheduledSync() : defaults.scheduledSync(),
                doc.getBackfillDays() != null ? doc.getBackfillDays() : defaults.backfillDays());
    }

    public OrganizationDocument toDocument(Organization org) {
        List<AiPriceEntryDocument> aiPriceDocs = org.getAiPrices() == null ? null :
                org.getAiPrices().stream()
                        .map(e -> new AiPriceEntryDocument(e.getModel(), e.getPricePerKInput(), e.getPricePerKOutput()))
                        .toList();

        var builder = OrganizationDocument.builder()
                .name(org.getName())
                .slug(org.getSlug())
                .sector(org.getSector())
                .country(org.getCountry())
                .seniorities(org.getSeniorities())
                .aiPrices(aiPriceDocs)
                .subscriptionCostPerUserUsd(org.getSubscriptionCostPerUserUsd())
                .active(org.isActive())
                .syncSettings(new SyncSettingsDocument(
                        org.getSyncSettings().scheduledSync(), org.getSyncSettings().backfillDays()))
                .lastScheduledSyncAt(org.getLastScheduledSyncAt())
                .deletedAt(org.getDeletedAt())
                .deletedBy(org.getDeletedBy())
                .purgedAt(org.getPurgedAt())
                .metricSettings(toMetricSettingsDocument(org.getMetricSettings()))
                .vcsSettings(toVcsSettingsDocument(org.getVcsSettings()))
                .workItemPatterns(toWorkItemPatternsDocument(org.getWorkItemPatterns()))
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt());

        if (org.getId() != null) {
            builder.id(org.getId());
            builder.version(org.getVersion());
        }

        return builder.build();
    }
}
