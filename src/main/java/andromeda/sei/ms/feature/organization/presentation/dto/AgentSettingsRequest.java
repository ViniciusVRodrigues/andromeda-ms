package andromeda.sei.ms.feature.organization.presentation.dto;

import java.util.List;

/** {@code branchPatterns} null or empty restores the defaults; validated in the use case (422). */
public record AgentSettingsRequest(List<String> branchPatterns) {}
