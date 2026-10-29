package andromeda.sei.ms.feature.organization.application.dto;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.AgentSettings;

import java.util.List;
import java.util.stream.IntStream;

/** Effective patterns (stored, or the defaults), the defaults, whether they are in use, and the named presets. */
public record AgentSettingsOutput(List<String> branchPatterns, List<String> defaults, boolean usingDefaults,
                                  List<Preset> presets) {

    /** A named default pattern; the ui classifies a stored pattern equal to a preset regex as that preset. */
    public record Preset(String id, String regex) {}

    /** Ids of the defaults, in the order of {@link BranchPatternMatcher#DEFAULTS}. */
    private static final List<String> PRESET_IDS = List.of("AB_HASH", "JIRA_KEY", "NUMBER");

    public static List<Preset> defaultPresets() {
        List<String> defaults = BranchPatternMatcher.DEFAULTS;
        return IntStream.range(0, defaults.size())
                .mapToObj(i -> new Preset(PRESET_IDS.get(i), defaults.get(i))).toList();
    }

    public static AgentSettingsOutput from(AgentSettings settings) {
        List<String> defaults = BranchPatternMatcher.DEFAULTS;
        return new AgentSettingsOutput(settings.usingDefaults() ? defaults : settings.branchPatterns(),
                defaults, settings.usingDefaults(), defaultPresets());
    }
}
