package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.usage.AgentRowDispositionPolicy.Disposition;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.aiusage.application.AiefEditorTools;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.aiusage.domain.AiefCoexistence.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiefCutoverPoliciesTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate CUTOVER = LocalDate.of(2026, 10, 29);
    static final Instant HOUR = Instant.parse("2026-10-28T14:00:00Z");

    AgentUsageQueries queries = mock(AgentUsageQueries.class);
    AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
    AiefEditorTools tools = AiefEditorTools.defaults();
    AgentDevice device = new AgentDevice();
    List<String> claudeEditors = tools.editorsOf("claude-code");
    List<String> codexEditors = tools.editorsOf("codex");

    @BeforeEach
    void setUp() {
        device.setId("dev-1");
        device.setOrganizationId(ORG);
        device.setUserId(USER);
        device.setTimezone("America/Sao_Paulo");
        device.setPairedAt(Instant.parse("2026-10-01T00:00:00Z"));
        device.setCutoverDay(CUTOVER);
    }

    private CutoverRowDispositionPolicy policy() {
        return new CutoverRowDispositionPolicy(queries, tools);
    }

    // ---- CutoverRowDispositionPolicy (per tool) --------------------------------------------------

    @Test
    void should_supersedeTheRow_when_beforeTheCutoverAndAiefHasTheSameTool() {
        when(queries.existsActiveAief(ORG, USER, LocalDate.of(2026, 10, 28), claudeEditors)).thenReturn(true);

        assertThat(policy().classify(device, USER, LocalDate.of(2026, 10, 28), HOUR, "claude-code"))
                .isEqualTo(Disposition.SUPERSEDED);
    }

    @Test
    void should_keepTheRow_when_beforeTheCutoverAndAiefOnlyHasAnotherTool() {
        when(queries.existsActiveAief(ORG, USER, LocalDate.of(2026, 10, 28), claudeEditors)).thenReturn(false);
        when(queries.existsActiveAief(ORG, USER, LocalDate.of(2026, 10, 28), codexEditors)).thenReturn(true);

        assertThat(policy().classify(device, USER, LocalDate.of(2026, 10, 28), HOUR, "claude-code"))
                .isEqualTo(Disposition.LIVE);
    }

    @Test
    void should_supersedeTheRow_when_fromTheCutoverAndTheSameToolIsInAValidatedAllocation() {
        when(queries.existsAiefInValidatedAllocation(ORG, USER, CUTOVER, claudeEditors)).thenReturn(true);

        assertThat(policy().classify(device, USER, CUTOVER, Instant.parse("2026-10-29T14:00:00Z"), "claude-code"))
                .isEqualTo(Disposition.SUPERSEDED);
    }

    @Test
    void should_notSupersede_when_fromTheCutoverAndTheAiefIsNotValidated() {
        when(queries.existsAiefInValidatedAllocation(ORG, USER, CUTOVER, claudeEditors)).thenReturn(false);
        when(queries.existsActiveAief(ORG, USER, CUTOVER, claudeEditors)).thenReturn(true);

        assertThat(policy().classify(device, USER, CUTOVER, Instant.parse("2026-10-29T14:00:00Z"), "claude-code"))
                .isEqualTo(Disposition.LIVE);
    }

    @Test
    void should_fallBackToThePairingRule_when_thereIsNoCutover() {
        device.setCutoverDay(null);
        device.setPairedAt(Instant.parse("2026-10-28T20:00:00Z"));

        assertThat(policy().classify(device, USER, LocalDate.of(2026, 10, 28), HOUR, "claude-code"))
                .isEqualTo(Disposition.HISTORY);
    }

    @Test
    void should_notSupersede_when_theToolHasNoAiefEditor() {
        assertThat(policy().classify(device, USER, LocalDate.of(2026, 10, 28), HOUR, "gemini"))
                .isEqualTo(Disposition.LIVE);
    }

    @Test
    void should_deactivateTheAiefOfTheSameTool_afterStoringOnAnAgentDay() {
        policy().afterStored(device, USER, CUTOVER, "claude-code");

        verify(queries).supersedeAief(ORG, USER, CUTOVER, claudeEditors);
    }

    @Test
    void should_notDeactivateAief_afterStoringOnAHistoricalDay() {
        policy().afterStored(device, USER, LocalDate.of(2026, 10, 28), "claude-code");

        verify(queries, never()).supersedeAief(any(), any(), any(), any());
    }

    // ---- AgentDayPolicy (AIEF verdict per entry) --------------------------------------------------

    private AgentDayPolicy dayPolicy() {
        when(devices.findDevicesOfUser(ORG, USER)).thenReturn(List.of(device));
        return new AgentDayPolicy(devices, queries, tools);
    }

    @Test
    void should_ignoreOnlyTheEntryOfAToolTheAgentAlreadyHas() {
        when(queries.existsActiveAgentRow(ORG, USER, CUTOVER, "claude-code")).thenReturn(true);

        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Claude Code CLI", "claude-sonnet")).isEqualTo(Verdict.IGNORE);
        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Claude (VS Code)", "claude-sonnet")).isEqualTo(Verdict.IGNORE);
    }

    @Test
    void should_storeTheCopilotEntry_evenWithAClaudeModel_onAnAgentDay() {
        when(queries.existsActiveAgentRow(ORG, USER, CUTOVER, "claude-code")).thenReturn(true);
        when(queries.agentModelsOfDay(ORG, USER, CUTOVER)).thenReturn(java.util.Set.of());

        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "VS Code", "claude-sonnet")).isEqualTo(Verdict.STORE);
    }

    @Test
    void should_storeTheToolEntry_when_theAgentHasNoRowOfThatToolYet() {
        when(queries.existsActiveAgentRow(ORG, USER, CUTOVER, "claude-code")).thenReturn(false);

        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Claude Code CLI", "m")).isEqualTo(Verdict.STORE);
    }

    @Test
    void should_storeTheToolEntry_onAHistoricalDay() {
        when(queries.existsActiveAgentRow(ORG, USER, LocalDate.of(2026, 10, 28), "claude-code")).thenReturn(true);

        assertThat(dayPolicy().verdict(ORG, USER, LocalDate.of(2026, 10, 28), "Claude Code CLI", "m"))
                .isEqualTo(Verdict.STORE);
    }

    @Test
    void should_flagAnEntryWithoutToolThatLooksLikeTheAgent() {
        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Claude Desktop", "x")).isEqualTo(Verdict.STORE_FLAGGED);
        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Codex Desktop", "x")).isEqualTo(Verdict.STORE_FLAGGED);

        when(queries.agentModelsOfDay(ORG, USER, CUTOVER)).thenReturn(java.util.Set.of("gpt-5"));
        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Cursor", "gpt-5")).isEqualTo(Verdict.STORE_FLAGGED);
        assertThat(dayPolicy().verdict(ORG, USER, CUTOVER, "Cursor", "other")).isEqualTo(Verdict.STORE);
    }

    @Test
    void should_changeNothing_when_thePersonHasNoDevice() {
        when(devices.findDevicesOfUser(ORG, USER)).thenReturn(List.of());

        assertThat(new AgentDayPolicy(devices, queries, tools).verdict(ORG, USER, CUTOVER, "Claude Code CLI", "m"))
                .isEqualTo(Verdict.STORE);
    }

    @Test
    void should_stopIgnoringAfterTheDayOfRevocation_when_theDeviceIsRevoked() {
        device.revoke(USER, Instant.parse("2026-10-31T15:00:00Z"));
        when(queries.existsActiveAgentRow(ORG, USER, LocalDate.of(2026, 11, 1), "claude-code")).thenReturn(true);
        when(queries.existsActiveAgentRow(ORG, USER, LocalDate.of(2026, 10, 31), "claude-code")).thenReturn(true);

        assertThat(dayPolicy().verdict(ORG, USER, LocalDate.of(2026, 10, 31), "Claude Code CLI", "m")).isEqualTo(Verdict.IGNORE);
        assertThat(dayPolicy().verdict(ORG, USER, LocalDate.of(2026, 11, 1), "Claude Code CLI", "m")).isEqualTo(Verdict.STORE);
    }
}
