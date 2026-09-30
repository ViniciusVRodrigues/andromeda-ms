package andromeda.sei.ms.feature.organization.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemLinkConfigTest {

    @Test
    void shouldApplyAzureBoardsPrefix_when_organizationConfiguredNothing() {
        WorkItemLinkConfig config = WorkItemLinkConfig.orDefaults(null);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getPrefixes()).containsExactly("AB#");
    }

    @Test
    void shouldUseConfiguredPrefix_when_organizationSetOne() {
        WorkItemLinkConfig config = new WorkItemLinkConfig(true, List.of("WI-"));

        assertThat(config.getPrefixes()).containsExactly("WI-");
    }

    @Test
    void shouldAcceptSeveralPrefixes_when_teamIsMigratingConventions() {
        WorkItemLinkConfig config = new WorkItemLinkConfig(true, List.of("AB#", "WI-"));

        assertThat(config.getPrefixes()).containsExactly("AB#", "WI-");
    }

    @Test
    void shouldExposeNoPrefixes_when_extractionIsDisabled() {
        WorkItemLinkConfig config = new WorkItemLinkConfig(false, List.of("AB#"));

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getPrefixes()).isEmpty();
    }

    /**
     * An enabled configuration carrying no prefix would match nothing at all, which reads as
     * "linking is on but silently broken". Falling back to the default keeps enabled meaning
     * enabled; an organization that wants no linking disables it instead.
     */
    @Test
    void shouldFallBackToDefault_when_enabledWithoutAnyPrefix() {
        WorkItemLinkConfig config = new WorkItemLinkConfig(true, List.of());

        assertThat(config.getPrefixes()).containsExactly("AB#");
    }

    @Test
    void shouldKeepStoredConfig_when_oneExists() {
        WorkItemLinkConfig stored = new WorkItemLinkConfig(false, List.of());

        assertThat(WorkItemLinkConfig.orDefaults(stored)).isSameAs(stored);
    }
}
