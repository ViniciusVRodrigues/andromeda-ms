package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.organization.application.dto.AgentSettingsOutput;

import java.util.List;

public record AgentSettingsResponse(List<String> branchPatterns, List<String> defaults, boolean usingDefaults,
                                    List<PresetResponse> presets) {

    public record PresetResponse(String id, String regex) {}

    public static AgentSettingsResponse from(AgentSettingsOutput output) {
        return new AgentSettingsResponse(output.branchPatterns(), output.defaults(), output.usingDefaults(),
                output.presets().stream().map(p -> new PresetResponse(p.id(), p.regex())).toList());
    }
}
