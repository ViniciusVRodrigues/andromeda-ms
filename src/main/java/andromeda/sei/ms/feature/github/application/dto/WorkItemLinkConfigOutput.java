package andromeda.sei.ms.feature.github.application.dto;

import java.util.List;

public record WorkItemLinkConfigOutput(boolean enabled, List<String> prefixes) {}
