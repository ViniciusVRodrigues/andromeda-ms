package andromeda.sei.ms.feature.allocation.application.dto;

import java.math.BigDecimal;

/** One task of a day's suggestion; {@code overriddenMark} is the task the person marked and the branch overruled. */
public record SuggestionItemOutput(String workItemId, String workItemTitle, BigDecimal percentage,
                                   String displayId, String source, OverriddenMark overriddenMark) {

    public record OverriddenMark(String displayId) {}
}
