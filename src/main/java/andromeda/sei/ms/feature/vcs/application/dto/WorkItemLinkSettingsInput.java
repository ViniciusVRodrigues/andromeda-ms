package andromeda.sei.ms.feature.vcs.application.dto;

import java.util.List;

public record WorkItemLinkSettingsInput(boolean enabled, List<String> prefixes) {}
