package andromeda.sei.ms.feature.github.application.dto;

import java.util.List;

public record WorkItemLinkConfigInput(boolean enabled, List<String> prefixes) {}
