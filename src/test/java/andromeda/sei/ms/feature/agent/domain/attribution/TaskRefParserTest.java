package andromeda.sei.ms.feature.agent.domain.attribution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRefParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"AB#123", "ab#123", "#123", "123", " AB#123 "})
    void should_normalizeAzureForms_when_numeric(String raw) {
        assertThat(TaskRefParser.parse(raw)).contains("123");
    }

    @Test
    void should_keepJiraKey() {
        assertThat(TaskRefParser.parse("PROJ-45")).contains("PROJ-45");
        assertThat(TaskRefParser.parse("proj-45")).contains("PROJ-45");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "abc", "AB#", "#", "AB#12x", "12 34", "PROJ-", "-45", "A-1", "feature/123"})
    void should_returnEmpty_when_notAReference(String raw) {
        assertThat(TaskRefParser.parse(raw)).isEmpty();
    }
}
