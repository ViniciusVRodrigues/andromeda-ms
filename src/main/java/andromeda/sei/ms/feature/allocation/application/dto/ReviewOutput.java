package andromeda.sei.ms.feature.allocation.application.dto;

import java.util.List;

/** {@code state} is SUGGESTED or NEEDS_REVIEW; reasons are the distinct codes, details the union of the day's reasons. */
public record ReviewOutput(String state, List<String> reasons, List<Detail> details) {

    public record Detail(String code, String workItemId, String displayId, Long ratio) {}
}
