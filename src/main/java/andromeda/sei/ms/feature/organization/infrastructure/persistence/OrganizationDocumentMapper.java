package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.MetricSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
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
        return organization;
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
                .createdAt(org.getCreatedAt())
                .updatedAt(org.getUpdatedAt());

        if (org.getId() != null) {
            builder.id(org.getId());
            builder.version(org.getVersion());
        }

        return builder.build();
    }
}
