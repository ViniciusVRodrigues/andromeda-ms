package andromeda.sei.ms.feature.vcs.presentation.dto;

import java.util.List;

public record WorkItemLinkSettingsRequest(boolean enabled, List<String> prefixes) {}
