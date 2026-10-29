package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import java.util.List;
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

    @Test
    void should_readDefaultPatterns_when_documentHasNothing() {
        Organization organization = mapper.toDomain(OrganizationDocument.builder().id("org-1").active(true).build());

        assertThat(organization.getWorkItemPatterns().mode()).isEqualTo(WorkItemPatterns.Mode.DEFAULT);
        assertThat(organization.getWorkItemPatterns().enabled()).isTrue();
        assertThat(organization.getWorkItemPatterns().regexes()).containsExactly(BranchPatternMatcher.AB_HASH_REGEX);
        assertThat(organization.getWorkItemPatterns().patterns().get(0).preset()).isEqualTo("AB_HASH");
        assertThat(organization.getWorkItemPatterns().patterns().get(0).alsoInText()).isTrue();
        assertThat(mapper.toDocument(organization).getWorkItemPatterns()).isNull();
    }

    @Test
    void should_fallBackToLegacyDefault_asModeDefault() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .vcsSettings(new VcsSettingsDocument(new VcsSettingsDocument.WorkItemLinkDocument(true, List.of("AB#"))))
                .build();

        WorkItemPatterns patterns = mapper.toDomain(doc).getWorkItemPatterns();

        assertThat(patterns.mode()).isEqualTo(WorkItemPatterns.Mode.DEFAULT);
        assertThat(patterns.regexes()).containsExactly(BranchPatternMatcher.AB_HASH_REGEX);
    }

    @Test
    void should_fallBackToLegacyCustomPrefixes_asLiteralPatternsInCustomMode() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .vcsSettings(new VcsSettingsDocument(
                        new VcsSettingsDocument.WorkItemLinkDocument(true, List.of("AB#", "[WI]-"))))
                .build();

        WorkItemPatterns patterns = mapper.toDomain(doc).getWorkItemPatterns();

        assertThat(patterns.mode()).isEqualTo(WorkItemPatterns.Mode.CUSTOM);
        assertThat(patterns.enabled()).isTrue();
        assertThat(patterns.patterns()).extracting(WorkItemPatterns.Entry::preset).containsExactly("AB_HASH", "PREFIX");
        assertThat(patterns.patterns()).allMatch(WorkItemPatterns.Entry::alsoInText);
        assertThat(patterns.patterns().get(1).regex()).isEqualTo(BranchPatternMatcher.prefixRegex("[WI]-"));
        assertThat(patterns.legacyPrefixes()).containsExactly("AB#", "[WI]-");
    }

    @Test
    void should_fallBackToLegacyDisabled_keepingEnabledFalse() {
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .vcsSettings(new VcsSettingsDocument(new VcsSettingsDocument.WorkItemLinkDocument(false, List.of("AB#"))))
                .build();

        WorkItemPatterns patterns = mapper.toDomain(doc).getWorkItemPatterns();

        assertThat(patterns.enabled()).isFalse();
        assertThat(patterns.mode()).isEqualTo(WorkItemPatterns.Mode.CUSTOM);
    }

    @Test
    void should_preferStoredPatterns_overLegacyAndRoundTripThem() {
        WorkItemPatternsDocument stored = new WorkItemPatternsDocument(true, "DETECTED",
                List.of(new WorkItemPatternsDocument.EntryDocument(BranchPatternMatcher.AB_HASH_REGEX, "AB_HASH", true),
                        new WorkItemPatternsDocument.EntryDocument(BranchPatternMatcher.NUMBER_REGEX, "NUMBER", false)),
                "user-1", Instant.parse("2026-10-29T10:00:00Z"), "user-1", Instant.parse("2026-10-29T10:00:00Z"), null);
        OrganizationDocument doc = OrganizationDocument.builder().id("org-1").active(true)
                .vcsSettings(new VcsSettingsDocument(new VcsSettingsDocument.WorkItemLinkDocument(true, List.of("WI-"))))
                .workItemPatterns(stored).build();

        Organization organization = mapper.toDomain(doc);

        assertThat(organization.getWorkItemPatterns().mode()).isEqualTo(WorkItemPatterns.Mode.DETECTED);
        assertThat(organization.getWorkItemPatterns().patterns()).extracting(WorkItemPatterns.Entry::preset)
                .containsExactly("AB_HASH", "NUMBER");
        OrganizationDocument back = mapper.toDocument(organization);
        assertThat(back.getWorkItemPatterns()).isEqualTo(stored);
        assertThat(back.getVcsSettings().getWorkItemLink().getPrefixes()).containsExactly("WI-");
    }
}
