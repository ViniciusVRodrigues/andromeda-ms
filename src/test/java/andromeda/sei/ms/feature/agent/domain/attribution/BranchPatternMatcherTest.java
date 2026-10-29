package andromeda.sei.ms.feature.agent.domain.attribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BranchPatternMatcherTest {

    private final BranchPatternMatcher defaults = BranchPatternMatcher.defaults();

    @Test
    void should_matchOnlyTheAzureBoardsConventionByDefault() {
        assertThat(defaults.match("feature/AB#123-x")).contains("123");
        assertThat(defaults.match("feature/ab#123-x")).contains("123");
        assertThat(defaults.match("feature/AB#12")).contains("12");
        assertThat(defaults.match("feature/1234-desc")).isEmpty();
        assertThat(defaults.match("PROJ-45-login")).isEmpty();
        assertThat(defaults.match("main")).isEmpty();
        assertThat(defaults.match(null)).isEmpty();
    }

    @Test
    void should_matchThePresetMolds() {
        var jiraThenNumber = BranchPatternMatcher.of(
                List.of(BranchPatternMatcher.JIRA_KEY_REGEX, BranchPatternMatcher.NUMBER_REGEX));
        assertThat(jiraThenNumber.match("PROJ-45-login")).contains("PROJ-45");
        assertThat(jiraThenNumber.match("feature/1234-desc")).contains("1234");
        assertThat(jiraThenNumber.match("bugfix/task-987")).contains("987");
        // release/2026 matches the numeric mold; without a work item 2026 in the sprint the agent does not resolve it
        assertThat(jiraThenNumber.match("release/2026")).contains("2026");
        assertThat(jiraThenNumber.match("main")).isEmpty();
    }

    @Test
    void should_useFirstPatternThatMatches_inOrder() {
        var matcher = BranchPatternMatcher.of(List.of("^wi-(\\d+)", "(\\d+)"));
        assertThat(matcher.match("wi-77")).contains("77");
        assertThat(matcher.match("x-5")).contains("5");
    }

    @Test
    void should_useDefaults_when_listNullOrEmpty() {
        assertThat(BranchPatternMatcher.of(null).match("AB#9")).contains("9");
        assertThat(BranchPatternMatcher.of(List.of()).match("AB#9")).contains("9");
    }

    @Test
    @Timeout(2)
    void should_notMatchAndReturnFast_when_regexIsCatastrophic() {
        var matcher = BranchPatternMatcher.of(List.of("(a+)+$"));
        assertThat(matcher.match("a".repeat(40) + "!")).isEmpty();
    }

    @Test
    void should_cutBranchAt255() {
        var matcher = BranchPatternMatcher.of(List.of("zz(\\d+)"));
        assertThat(matcher.match("a".repeat(255) + "zz12")).isEmpty();
        assertThat(matcher.match("a".repeat(250) + "zz12")).contains("12");
    }

    @Test
    void should_validateAndReportEveryError() {
        assertThat(BranchPatternMatcher.validateAll(null)).isEmpty();
        assertThat(BranchPatternMatcher.validateAll(List.of())).isEmpty();
        assertThat(BranchPatternMatcher.validateAll(BranchPatternMatcher.DEFAULTS)).isEmpty();
        var errors = BranchPatternMatcher.validateAll(List.of("ok(\\d+)", "feature/\\d+", "(unclosed", " ", "(" + "a".repeat(200) + ")"));
        assertThat(errors).extracting(BranchPatternMatcher.Error::index).containsExactly(1, 2, 3, 4);
        assertThat(errors).extracting(BranchPatternMatcher.Error::code).containsExactly(
                BranchPatternMatcher.ErrorCode.PATTERN_NO_GROUP, BranchPatternMatcher.ErrorCode.PATTERN_INVALID,
                BranchPatternMatcher.ErrorCode.PATTERN_INVALID, BranchPatternMatcher.ErrorCode.PATTERN_TOO_LONG);
        List<String> eleven = IntStream.range(0, 11).mapToObj(i -> "(" + i + ")").toList();
        var many = BranchPatternMatcher.validateAll(eleven);
        assertThat(many).hasSize(1);
        assertThat(many.get(0).index()).isEqualTo(10);
        assertThat(many.get(0).code()).isEqualTo(BranchPatternMatcher.ErrorCode.PATTERN_TOO_MANY);
        assertThat(BranchPatternMatcher.validateAll(eleven.subList(0, 10))).isEmpty();
    }

    @Test
    void should_reportPatternIndexAndSpan() {
        var hit = defaults.matchDetailed("feature/AB#12-x").orElseThrow();
        assertThat(hit.group()).isEqualTo("12");
        assertThat(hit.patternIndex()).isZero();
        assertThat(hit.start()).isEqualTo(8);
        assertThat(hit.end()).isEqualTo(13);
    }
}
