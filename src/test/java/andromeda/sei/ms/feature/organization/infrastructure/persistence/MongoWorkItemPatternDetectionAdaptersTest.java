package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsPullRequestDocument;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class MongoWorkItemPatternDetectionAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final Instant SINCE = NOW.minusSeconds(90L * 86400);

    @Autowired private MongoTemplate mongoTemplate;
    private MongoWorkItemPatternSampleSource sample;
    private MongoWorkItemReferenceLookup lookup;
    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void setUp() {
        sample = new MongoWorkItemPatternSampleSource(mongoTemplate);
        lookup = new MongoWorkItemReferenceLookup(mongoTemplate);
        mongoTemplate.remove(new Query(), VcsPullRequestDocument.COLLECTION);
        mongoTemplate.remove(new Query(), "ai_usages");
        mongoTemplate.remove(new Query(), "work_items");
    }

    private void pr(String org, String branch, Instant createdAt, boolean active) {
        mongoTemplate.insert(VcsPullRequestDocument.builder().organizationId(org).repositoryId("r1")
                .externalId("pr-" + seq.incrementAndGet()).sourceBranch(branch).createdAt(createdAt).active(active)
                .build());
    }

    private void agentRow(String org, String branch, LocalDate day, boolean active, String source) {
        mongoTemplate.insert(AiUsageDocument.builder().organizationId(org).userId("u1").deviceId("d1")
                .agentKey("k" + seq.incrementAndGet()).model("m" + seq.incrementAndGet()).source(source).branch(branch).day(day).active(active).build());
    }

    private void item(String org, String externalId, String title, boolean active) {
        mongoTemplate.insert(WorkItemDocument.builder().organizationId(org).externalId(externalId).title(title)
                .source(WorkItem.Source.values()[0]).active(active).build());
    }

    @Test
    void should_returnDistinctBranchesOfTheOrganizationOnly_activeAndWithinTheWindow() {
        pr("org-1", "feature/1-a", NOW.minusSeconds(3600), true);
        pr("org-1", "feature/1-a", NOW.minusSeconds(7200), true);   // duplicate
        pr("org-1", "feature/old", SINCE.minusSeconds(86400), true); // outside the 90 days
        pr("org-1", "feature/inactive", NOW.minusSeconds(60), false);
        pr("org-1", null, NOW.minusSeconds(60), true);
        pr("org-2", "feature/other-org", NOW.minusSeconds(60), true);
        agentRow("org-1", "feature/2-b", LocalDate.of(2026, 10, 28), true, "AGENT");
        agentRow("org-1", "feature/3-aiefish", LocalDate.of(2026, 10, 28), true, "AIEF");
        agentRow("org-1", "feature/4-old", LocalDate.of(2026, 1, 1), true, "AGENT");
        agentRow("org-1", "feature/5-inactive", LocalDate.of(2026, 10, 28), false, "AGENT");
        agentRow("org-2", "feature/6-other", LocalDate.of(2026, 10, 28), true, "AGENT");
        agentRow("org-1", "feature/1-a", LocalDate.of(2026, 10, 28), true, "AGENT"); // same branch as a PR

        List<String> branches = sample.recentBranches("org-1", SINCE, 5000);

        assertThat(branches).containsExactlyInAnyOrder("feature/1-a", "feature/2-b");
    }

    @Test
    void should_keepTheMostRecentBranches_whenOverTheLimit() {
        for (int i = 0; i < 6; i++) pr("org-1", "feature/" + i, NOW.minusSeconds(100L * (i + 1)), true);

        List<String> branches = sample.recentBranches("org-1", SINCE, 3);

        assertThat(branches).containsExactly("feature/0", "feature/1", "feature/2");
    }

    @Test
    void should_returnTitlesOfActiveItemsOfTheOrganization_forTheGivenIds() {
        item("org-1", "100", "First", true);
        item("org-1", "101", "Inactive", false);
        item("org-2", "102", "Other org", true);
        item("org-1", "103", "Not asked", true);

        Map<String, String> titles = lookup.activeTitlesByExternalId("org-1", Set.of("100", "101", "102", "999"));

        assertThat(titles).containsOnlyKeys("100").containsEntry("100", "First");
        assertThat(lookup.activeTitlesByExternalId("org-1", Set.of())).isEmpty();
    }

    @Test
    void should_detectJiraStyleExternalIds_onlyActiveAndOfTheOrganization() {
        item("org-1", "123", "numeric", true);
        item("org-2", "PROJ-9", "other org", true);
        item("org-1", "PROJ-8", "inactive", false);
        assertThat(lookup.hasJiraStyleExternalIds("org-1")).isFalse();

        item("org-1", "PROJ-7", "active", true);
        assertThat(lookup.hasJiraStyleExternalIds("org-1")).isTrue();
    }
}
