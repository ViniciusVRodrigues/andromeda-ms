package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parity (design 14.3): the extractor over the migrated patterns links the same externalId as the
 * prefix based extractor it replaces, for a table of pull requests across configurations. The
 * stricter new rules (alsoInText false, presets) are covered at the end.
 */
class WorkItemReferenceParityTest {

    private record Pr(String branch, String body, String title) {}

    private static final List<Pr> PRS = List.of(
            new Pr("feature/AB#123-add-repos-tab", null, null),
            new Pr("feature/repos-tab", "Closes AB#77", "Add repos tab"),
            new Pr("feature/repos-tab", "no reference", "AB#9 Add repos tab"),
            new Pr("feature/AB#1-first", "Closes AB#2", "AB#3 title"),
            new Pr("fix/wiql", null, "Fix Azure work item sync failing with 400 on every WIQL query"),
            new Pr("fix/wiql", "Follow-up to #11", "Build the JAR natively"),
            new Pr("feature/AB#12", null, null),
            new Pr("feature/AB#1234-x", null, null),
            new Pr("feature/x", "AB#12", null),
            new Pr("feature/x", "see AB#1234", null),
            new Pr("feature/x", null, "AB#12 and AB#1234"),
            new Pr("feature/ab#55-lowercase", null, null),
            new Pr("feature/WI-42-x", null, null),
            new Pr("feature/x", "wi-43", "t"),
            new Pr("feature/[WI]31-x", null, null),
            new Pr("feature/x", "[wi]32", null),
            new Pr("feature/AB#6-x", "WI-7", "AB#8"),
            new Pr("feature/x", "WI-7 then AB#8", null),
            new Pr("feature/x", null, "Fix 400 error"),
            new Pr("main", null, "Release 2026"),
            new Pr(null, null, null),
            new Pr("", "", ""),
            new Pr("feature/task_99-x", null, "task_100"),
            new Pr("feature/a.b\\E-5", null, null));

    private static final Map<String, WorkItemLinkConfig> CONFIGS = new LinkedHashMap<>();

    static {
        CONFIGS.put("default", WorkItemLinkConfig.defaults());
        CONFIGS.put("default enabled, no prefixes", new WorkItemLinkConfig(true, List.of()));
        CONFIGS.put("custom prefix", new WorkItemLinkConfig(true, List.of("WI-")));
        CONFIGS.put("several prefixes", new WorkItemLinkConfig(true, List.of("AB#", "WI-")));
        CONFIGS.put("several, other order", new WorkItemLinkConfig(true, List.of("WI-", "AB#", "task_")));
        CONFIGS.put("disabled", new WorkItemLinkConfig(false, List.of("AB#")));
        CONFIGS.put("metacharacter [WI]", new WorkItemLinkConfig(true, List.of("[WI]")));
        CONFIGS.put("metacharacter with quote end", new WorkItemLinkConfig(true, List.of("a.b\\E-")));
        CONFIGS.put("padded prefix", new WorkItemLinkConfig(true, List.of("  WI-  ")));
        CONFIGS.put("blank prefixes ignored", new WorkItemLinkConfig(true, java.util.Arrays.asList("", "  ", "AB#")));
    }

    @Test
    void should_linkTheSameExternalId_asThePreviousExtractor_forEveryPullRequestAndConfiguration() {
        List<String> differences = new ArrayList<>();
        int compared = 0;
        for (var config : CONFIGS.entrySet()) {
            WorkItemPatterns migrated = WorkItemPatterns.fromLegacy(config.getValue());
            for (Pr pr : PRS) {
                Optional<String> expected = LegacyWorkItemReferenceExtractor
                        .extract(config.getValue(), pr.branch(), pr.body(), pr.title());
                Optional<String> actual = WorkItemReferenceExtractor
                        .extract(migrated, pr.branch(), pr.body(), pr.title());
                compared++;
                if (!expected.equals(actual)) {
                    differences.add(config.getKey() + " / " + pr + ": expected " + expected + " but was " + actual);
                }
            }
        }
        assertThat(compared).isEqualTo(CONFIGS.size() * PRS.size());
        assertThat(differences).isEmpty();
    }

    @Test
    void should_keepAnOrganizationWithoutAnyConfiguration_onTheOldDefault() {
        for (Pr pr : PRS) {
            assertThat(WorkItemReferenceExtractor.extract(WorkItemPatterns.defaults(), pr.branch(), pr.body(), pr.title()))
                    .as(pr.toString())
                    .isEqualTo(LegacyWorkItemReferenceExtractor.extract(null, pr.branch(), pr.body(), pr.title()));
        }
    }

    @Test
    void should_keepANumberOnlyPatternOutOfTitleAndBody_whenAlsoInTextIsOff() {
        WorkItemPatterns number = new WorkItemPatterns(true, WorkItemPatterns.Mode.CUSTOM,
                List.of(WorkItemPatterns.Entry.of(BranchPatternMatcher.NUMBER_REGEX, false)),
                null, null, null, null, null);

        assertThat(WorkItemReferenceExtractor.extract(number, "main", null, "Fix 400 error")).isEmpty();
        assertThat(WorkItemReferenceExtractor.extract(number, "feature/1234-login", null, "Fix 400 error"))
                .contains("1234");
    }

    @Test
    void should_runANumberPatternOverTheTitle_whenAlsoInTextIsOn() {
        WorkItemPatterns number = new WorkItemPatterns(true, WorkItemPatterns.Mode.CUSTOM,
                List.of(WorkItemPatterns.Entry.of("(?i)ticket (\\d+)", true)), null, null, null, null, null);

        assertThat(WorkItemReferenceExtractor.extract(number, "main", null, "Fixes ticket 88")).contains("88");
    }

    @Test
    void should_classifyPresetsByMold() {
        assertThat(BranchPatternMatcher.classify(BranchPatternMatcher.AB_HASH_REGEX)).isEqualTo("AB_HASH");
        assertThat(BranchPatternMatcher.classify(BranchPatternMatcher.JIRA_KEY_REGEX)).isEqualTo("JIRA_KEY");
        assertThat(BranchPatternMatcher.classify(BranchPatternMatcher.NUMBER_REGEX)).isEqualTo("NUMBER");
        assertThat(BranchPatternMatcher.classify(BranchPatternMatcher.prefixRegex("task-"))).isEqualTo("PREFIX");
        assertThat(BranchPatternMatcher.classify("feature/(\\d+)")).isEqualTo("CUSTOM");
        assertThat(BranchPatternMatcher.literalPrefix(BranchPatternMatcher.prefixRegex("task-"))).contains("task-");
    }
}
