package andromeda.sei.ms.feature.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns.Entry;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkItemPatternsOrderTest {

    private static WorkItemPatterns of(List<Entry> entries) {
        return new WorkItemPatterns(true, WorkItemPatterns.Mode.CUSTOM, entries, null, null, null, null, null);
    }

    private static List<String> presets(WorkItemPatterns p) {
        return p.patterns().stream().map(Entry::preset).toList();
    }

    @Test
    void should_orderBySpecificityAbHashPrefixJiraCustomNumber() {
        WorkItemPatterns p = of(List.of(
                Entry.of(BranchPatternMatcher.NUMBER_REGEX, false),
                Entry.of("(?i)task_(\\d+)", false),
                WorkItemPatterns.fromPrefix("WI-"),
                Entry.of(BranchPatternMatcher.JIRA_KEY_REGEX, false),
                Entry.of(BranchPatternMatcher.AB_HASH_REGEX, true)));
        assertThat(presets(p)).containsExactly("PREFIX", "AB_HASH", "JIRA_KEY", "CUSTOM", "NUMBER");
    }

    @Test
    void should_keepSentOrderBetweenAbHashAndPrefix() {
        WorkItemPatterns p = of(List.of(WorkItemPatterns.fromPrefix("WI-"), Entry.of(BranchPatternMatcher.AB_HASH_REGEX, true)));
        assertThat(presets(p)).containsExactly("PREFIX", "AB_HASH");
    }

    @Test
    void should_keepSentOrderAmongPrefixes() {
        WorkItemPatterns p = of(List.of(
                WorkItemPatterns.fromPrefix("WI-"),
                WorkItemPatterns.fromPrefix("TASK-"),
                WorkItemPatterns.fromPrefix("BUG-")));
        assertThat(p.patterns().stream().map(Entry::regex).toList()).containsExactly(
                BranchPatternMatcher.prefixRegex("WI-"),
                BranchPatternMatcher.prefixRegex("TASK-"),
                BranchPatternMatcher.prefixRegex("BUG-"));
    }

    @Test
    void should_keepSentOrderAmongCustoms() {
        WorkItemPatterns p = of(List.of(
                Entry.of("(?i)b_(\\d+)", false), Entry.of("(?i)a_(\\d+)", false)));
        assertThat(p.patterns().stream().map(Entry::regex).toList())
                .containsExactly("(?i)b_(\\d+)", "(?i)a_(\\d+)");
    }

    @Test
    void should_extractTheJiraKeyWhateverTheOrderSent() {
        Entry jira = Entry.of(BranchPatternMatcher.JIRA_KEY_REGEX, false);
        Entry number = Entry.of(BranchPatternMatcher.NUMBER_REGEX, false);
        for (List<Entry> sent : List.of(List.of(jira, number), List.of(number, jira))) {
            WorkItemPatterns p = of(sent);
            assertThat(BranchPatternMatcher.of(p.regexes()).match("feat/PROJ-88_foo")).contains("PROJ-88");
        }
    }
}
