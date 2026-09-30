package andromeda.sei.ms.feature.vcs.application;

import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemReferenceExtractorTest {

    private static final WorkItemLinkConfig DEFAULTS = WorkItemLinkConfig.defaults();

    @Test
    void shouldExtractFromBranch() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                DEFAULTS, "feature/AB#123-add-repos-tab", null, null);

        assertThat(id).contains("123");
    }

    @Test
    void shouldExtractFromBody_when_branchHasNoReference() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                DEFAULTS, "feature/repos-tab", "Closes AB#77", "Add repos tab");

        assertThat(id).contains("77");
    }

    @Test
    void shouldExtractFromTitle_when_branchAndBodyHaveNoReference() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                DEFAULTS, "feature/repos-tab", "no reference here", "AB#9 Add repos tab");

        assertThat(id).contains("9");
    }

    /**
     * The branch is the most reliable source, so a reference there wins over one in the body
     * even when both are present.
     */
    @Test
    void shouldPreferBranch_when_severalSourcesCarryAReference() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                DEFAULTS, "feature/AB#1-first", "Closes AB#2", "AB#3 title");

        assertThat(id).contains("1");
    }

    /**
     * Real pull request titles from this project. Azure work item ids are small sequential
     * integers, so a bare-number match would turn an HTTP status code into a work item link.
     */
    @Test
    void shouldIgnoreBareNumbers_when_titleMentionsUnrelatedFigures() {
        Optional<String> httpStatus = WorkItemReferenceExtractor.extract(
                DEFAULTS, "fix/wiql", null,
                "Fix Azure work item sync failing with 400 on every WIQL query");

        Optional<String> otherPr = WorkItemReferenceExtractor.extract(
                DEFAULTS, "fix/wiql", "Follow-up to #11", "Build the JAR natively");

        assertThat(httpStatus).isEmpty();
        assertThat(otherPr).isEmpty();
    }

    @Test
    void shouldReturnEmpty_when_noSourceCarriesAReference() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                DEFAULTS, "feature/repos-tab", "just a description", "Add repos tab");

        assertThat(id).isEmpty();
    }

    @Test
    void shouldReturnEmpty_when_extractionIsDisabled() {
        WorkItemLinkConfig disabled = new WorkItemLinkConfig(false, List.of("AB#"));

        Optional<String> id = WorkItemReferenceExtractor.extract(
                disabled, "feature/AB#123-add-repos-tab", null, null);

        assertThat(id).isEmpty();
    }

    @Test
    void shouldHonourCustomPrefix() {
        WorkItemLinkConfig custom = new WorkItemLinkConfig(true, List.of("WI-"));

        assertThat(WorkItemReferenceExtractor.extract(custom, "feature/WI-42-x", null, null))
                .contains("42");
        assertThat(WorkItemReferenceExtractor.extract(custom, "feature/AB#42-x", null, null))
                .isEmpty();
    }

    @Test
    void shouldMatchAnyConfiguredPrefix_when_teamIsMigratingConventions() {
        WorkItemLinkConfig both = new WorkItemLinkConfig(true, List.of("AB#", "WI-"));

        assertThat(WorkItemReferenceExtractor.extract(both, "feature/WI-5-x", null, null))
                .contains("5");
        assertThat(WorkItemReferenceExtractor.extract(both, "feature/AB#6-x", null, null))
                .contains("6");
    }

    /** A shorter id must not match inside a longer one. */
    @Test
    void shouldNotMatchPartialNumber() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                DEFAULTS, "feature/AB#1234-x", null, null);

        assertThat(id).contains("1234");
    }

    @Test
    void shouldApplyDefaults_when_configIsNull() {
        Optional<String> id = WorkItemReferenceExtractor.extract(
                null, "feature/AB#8-x", null, null);

        assertThat(id).contains("8");
    }

    @Test
    void shouldTolerateNullSources() {
        assertThat(WorkItemReferenceExtractor.extract(DEFAULTS, null, null, null)).isEmpty();
    }

    /**
     * A configured prefix carrying regex metacharacters must be matched literally rather than
     * interpreted as a pattern.
     */
    @Test
    void shouldTreatPrefixLiterally_when_itCarriesRegexMetacharacters() {
        WorkItemLinkConfig bracketed = new WorkItemLinkConfig(true, List.of("[WI]"));

        assertThat(WorkItemReferenceExtractor.extract(bracketed, "feature/[WI]31-x", null, null))
                .contains("31");
    }
}
