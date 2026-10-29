package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiefCoexistence;
import andromeda.sei.ms.feature.aiusage.domain.AiefCoexistence.Verdict;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AIEF coexistence (agente-endpoint-v2, design 6): per tool: only the entry of a tool the agent already has is ignored. */
class UpsertAIUsageAgentCutoffTest {

    static final LocalDate BEFORE = LocalDate.of(2026, 10, 28);
    static final LocalDate CUTOVER = LocalDate.of(2026, 10, 29);

    AIUsageRepository repository = mock(AIUsageRepository.class);
    AutoAssignAllocationUseCase autoAssign = mock(AutoAssignAllocationUseCase.class);
    OrganizationRepository organizations = mock(OrganizationRepository.class);

    UpsertAIUsageUseCase useCase(AiefCoexistence coexistence) {
        when(organizations.findById("org-1")).thenReturn(Optional.empty());
        return new UpsertAIUsageUseCase(repository, autoAssign, new CalculateAiCostUseCase(), organizations, coexistence);
    }

    static AIUsageEntry entry(LocalDate day, String editor, String model) {
        return new AIUsageEntry(null, "ds", day, model, "ws", null, "m", null, 700L, 1000L, 1, editor);
    }

    @Test
    void should_storeTheCopilotEntry_and_ignoreOnlyTheClaudeCodeOne_onAnAgentDay() {
        UpsertAIUsageUseCase useCase = useCase((org, user, day, editor, model) ->
                "Claude Code CLI".equals(editor) ? Verdict.IGNORE : Verdict.STORE);

        int ignored = useCase.execute(List.of(
                entry(CUTOVER, "VS Code", "claude-sonnet-4"),
                entry(CUTOVER, "Claude Code CLI", "claude-sonnet-4")), "org-1", "user-1");

        assertThat(ignored).isEqualTo(1);
        ArgumentCaptor<AIUsage> stored = ArgumentCaptor.forClass(AIUsage.class);
        verify(repository).upsert(stored.capture());
        assertThat(stored.getAllValues()).extracting(AIUsage::getEditor).containsExactly("VS Code");
        assertThat(stored.getValue().isActive()).isTrue();
        assertThat(stored.getValue().isPossibleDuplicate()).isFalse();
        verify(autoAssign).execute("org-1", "user-1", CUTOVER);
    }

    @Test
    void should_storeFlagged_when_theEntryHasNoToolButLooksLikeTheAgents() {
        UpsertAIUsageUseCase useCase = useCase((org, user, day, editor, model) ->
                "Claude Desktop".equals(editor) ? Verdict.STORE_FLAGGED : Verdict.STORE);

        int ignored = useCase.execute(List.of(
                entry(CUTOVER, "Claude Desktop", "claude-sonnet-4"),
                entry(CUTOVER, "Cursor", "gpt-4o")), "org-1", "user-1");

        assertThat(ignored).isZero();
        ArgumentCaptor<AIUsage> stored = ArgumentCaptor.forClass(AIUsage.class);
        verify(repository, org.mockito.Mockito.times(2)).upsert(stored.capture());
        assertThat(stored.getAllValues()).filteredOn(u -> "Claude Desktop".equals(u.getEditor()))
                .singleElement().satisfies(u -> assertThat(u.isPossibleDuplicate()).isTrue());
        assertThat(stored.getAllValues()).filteredOn(u -> "Cursor".equals(u.getEditor()))
                .singleElement().satisfies(u -> assertThat(u.isPossibleDuplicate()).isFalse());
    }

    @Test
    void should_changeNothing_when_thePersonHasNoDevice() {
        UpsertAIUsageUseCase useCase = useCase((org, user, day, editor, model) -> Verdict.STORE);

        int ignored = useCase.execute(List.of(entry(BEFORE, "Claude Code CLI", "m"), entry(CUTOVER, "Claude Code CLI", "m")),
                "org-1", "user-1");

        assertThat(ignored).isZero();
        verify(autoAssign).execute("org-1", "user-1", BEFORE);
        verify(autoAssign).execute("org-1", "user-1", CUTOVER);
    }

    @Test
    void should_storeNothing_when_everyEntryIsIgnored() {
        UpsertAIUsageUseCase useCase = useCase((org, user, day, editor, model) -> Verdict.IGNORE);

        int ignored = useCase.execute(List.of(entry(CUTOVER, "Claude Code CLI", "m")), "org-1", "user-1");

        assertThat(ignored).isEqualTo(1);
        verify(autoAssign, never()).execute(any(), any(), any());
    }
}
