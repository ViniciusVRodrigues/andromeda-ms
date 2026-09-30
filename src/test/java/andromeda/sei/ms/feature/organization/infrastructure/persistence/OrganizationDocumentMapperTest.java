package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationDocumentMapperTest {

    private final OrganizationDocumentMapper mapper = new OrganizationDocumentMapper();

    @Test
    void shouldReadDefaultsWhenDocumentHasNoSyncSettings() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").name("Org").slug("org").active(true).build();

        Organization organization = mapper.toDomain(doc);

        assertThat(organization.getSyncSettings()).isEqualTo(SyncSettings.defaults());
        assertThat(organization.getSyncSettings().scheduledSync()).isEqualTo(ScheduledSyncFrequency.OFF);
        assertThat(organization.getSyncSettings().backfillDays()).isEqualTo(90);
    }

    @Test
    void shouldFillMissingBackfillDaysWithDefault() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .syncSettings(new SyncSettingsDocument(ScheduledSyncFrequency.DAILY, null)).build();

        Organization organization = mapper.toDomain(doc);

        assertThat(organization.getSyncSettings())
                .isEqualTo(new SyncSettings(ScheduledSyncFrequency.DAILY, SyncSettings.DEFAULT_BACKFILL_DAYS));
    }

    @Test
    void shouldAlwaysWriteSyncSettingsSoNewOrganizationsAreNotMigratedToDaily() {
        Instant now = Instant.now();
        Organization organization = new Organization(null, "Org", "org", null, null, null, null, null,
                true, null, now, now);

        OrganizationDocument doc = mapper.toDocument(organization);

        assertThat(doc.getSyncSettings()).isNotNull();
        assertThat(doc.getSyncSettings().getScheduledSync()).isEqualTo(ScheduledSyncFrequency.OFF);
        assertThat(doc.getSyncSettings().getBackfillDays()).isEqualTo(90);
    }

    @Test
    void shouldRoundTripLastScheduledSyncAt() {
        Instant tick = Instant.parse("2026-09-30T12:00:00Z");
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .syncSettings(new SyncSettingsDocument(ScheduledSyncFrequency.EVERY_6_HOURS, 30))
                .lastScheduledSyncAt(tick).build();

        OrganizationDocument back = mapper.toDocument(mapper.toDomain(doc));

        assertThat(back.getLastScheduledSyncAt()).isEqualTo(tick);
        assertThat(back.getSyncSettings().getBackfillDays()).isEqualTo(30);
    }

    @Test
    void should_readDefaultMetricSettings_when_documentHasNone() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true).build();

        Organization organization = mapper.toDomain(doc);

        assertThat(organization.getMetricSettings())
                .isEqualTo(andromeda.sei.ms.feature.organization.domain.MetricSettings.defaults());
    }

    @Test
    void should_roundTripMetricSettings_when_configured() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .metricSettings(new MetricSettingsDocument(
                        new RemediationRulesDocument(java.util.List.of("fix/prod-"), false, null)))
                .build();

        Organization organization = mapper.toDomain(doc);
        var rules = organization.getMetricSettings().remediationRules();
        assertThat(rules.branchPrefixes()).containsExactly("fix/prod-");
        assertThat(rules.bugLinkedCountsAsRemediation()).isFalse();
        assertThat(rules.revertCountsAsRemediation()).isTrue();

        OrganizationDocument back = mapper.toDocument(organization);
        assertThat(back.getMetricSettings().getRemediationRules().getBranchPrefixes()).containsExactly("fix/prod-");
        assertThat(back.getMetricSettings().getRemediationRules().getRevertCountsAsRemediation()).isTrue();
    }
}
