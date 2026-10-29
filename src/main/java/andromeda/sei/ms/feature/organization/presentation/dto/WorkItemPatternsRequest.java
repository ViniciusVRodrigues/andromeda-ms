package andromeda.sei.ms.feature.organization.presentation.dto;

import java.util.List;

/** {@code patterns} null or empty restores the defaults; validated in the use case (422). */
public record WorkItemPatternsRequest(Boolean enabled, List<WorkItemPatternInput> patterns) {}
