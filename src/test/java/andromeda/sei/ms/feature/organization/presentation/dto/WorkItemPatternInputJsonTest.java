package andromeda.sei.ms.feature.organization.presentation.dto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Deserialization with the JsonMapper Spring Boot configures (the one the real application uses). */
@JsonTest
class WorkItemPatternInputJsonTest {

    /** The regex as Java text, and the same regex escaped as a JSON string literal. */
    private static final String REGEX = "(?i)\\QAB#\\E(\\d+)(?!\\d)";
    private static final String REGEX_JSON = REGEX.replace("\\", "\\\\");

    @Autowired
    JsonMapper mapper;

    @Test
    void should_readObjectForm_when_putBodyHasRegexAndAlsoInText() throws Exception {
        var r = mapper.readValue(
                "{\"enabled\":true,\"patterns\":[{\"regex\":\"" + REGEX_JSON + "\",\"alsoInText\":true}]}", Body.class);
        assertThat(r.patterns()).hasSize(1);
        assertThat(r.patterns().get(0).regex()).isEqualTo(REGEX);
        assertThat(r.patterns().get(0).alsoInText()).isTrue();
    }

    @Test
    void should_readObjectWithoutAlsoInText() throws Exception {
        var r = mapper.readValue("{\"patterns\":[{\"regex\":\"" + REGEX_JSON + "\"}]}", Body.class);
        assertThat(r.patterns().get(0).regex()).isEqualTo(REGEX);
        assertThat(r.patterns().get(0).alsoInText()).isNull();
    }

    @Test
    void should_readBareString() throws Exception {
        var r = mapper.readValue("{\"patterns\":[\"" + REGEX_JSON + "\"]}", Body.class);
        assertThat(r.patterns().get(0).regex()).isEqualTo(REGEX);
        assertThat(r.patterns().get(0).alsoInText()).isNull();
    }

    @Test
    void should_readEmptyList() throws Exception {
        assertThat(mapper.readValue("{\"patterns\":[]}", Body.class).patterns()).isEmpty();
    }

    @Test
    void should_rejectNumber() {
        assertThatThrownBy(() -> mapper.readValue("{\"patterns\":[42]}", Body.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void should_readPreviewRequest() throws Exception {
        var r = mapper.readValue("{\"patterns\":[{\"regex\":\"" + REGEX_JSON + "\",\"alsoInText\":true}],"
                + "\"branches\":[\"AB#10-x\"]}", WorkItemPatternsPreviewRequest.class);
        assertThat(r.patterns()).hasSize(1);
        assertThat(r.branches()).containsExactly("AB#10-x");
    }

    @Test
    void should_ignoreUnknownFields_like_presetFromTheGetResponse() throws Exception {
        var r = mapper.readValue("{\"patterns\":[{\"regex\":\"" + REGEX_JSON
                + "\",\"preset\":\"AB_HASH\",\"alsoInText\":true,\"x\":1}]}", Body.class);
        assertThat(r.patterns().get(0).regex()).isEqualTo(REGEX);
    }

    @Test
    void should_readWithJackson2Too() throws Exception {
        var m2 = new com.fasterxml.jackson.databind.ObjectMapper();
        var r = m2.readValue("{\"patterns\":[{\"regex\":\"" + REGEX_JSON + "\",\"alsoInText\":true}]}", Body.class);
        assertThat(r.patterns().get(0).regex()).isEqualTo(REGEX);
    }

    record Body(Boolean enabled, List<WorkItemPatternInput> patterns) {}
}
