package andromeda.sei.ms.feature.organization.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code {regex, alsoInText?}}; a bare string is accepted as a regex too. Preset is computed by the server. */
public record WorkItemPatternInput(String regex, Boolean alsoInText) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public WorkItemPatternInput(@JsonProperty("regex") String regex, @JsonProperty("alsoInText") Boolean alsoInText) {
        this.regex = regex;
        this.alsoInText = alsoInText;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WorkItemPatternInput ofRegex(String regex) {
        return new WorkItemPatternInput(regex, null);
    }
}
