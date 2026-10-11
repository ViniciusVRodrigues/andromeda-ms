package andromeda.sei.ms.feature.dashboard.application.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** dashboards-customizaveis 1.9 — commit type from the conventional-commit prefix of the message. */
class CommitKindsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "feat: login com SSO|feat",
            "fix(auth): token expirado|fix",
            "FIX: maiúsculas|fix",
            "refactor!: troca o cliente|refactor",
            "test(api): cobre 404|test",
            "chore(deps): bump|chore",
            "docs: README|docs",
            "  feat: com espaço antes|feat",
            "perf: cache|other",
            "Merge pull request #12|other",
            "fixed the bug|other",
            "feat sem dois-pontos|other"})
    void should_classifyByTheConventionalPrefix(String message, String kind) {
        assertThat(CommitKinds.kindOf(message)).isEqualTo(kind);
    }

    @Test
    void should_readOnlyTheFirstLine_andTreatNullAsOther() {
        assertThat(CommitKinds.kindOf("Merge branch\n\nfix: inside the body")).isEqualTo("other");
        assertThat(CommitKinds.kindOf(null)).isEqualTo("other");
    }
}
