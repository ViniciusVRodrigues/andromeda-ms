package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.AiefEditorTools;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.AgentCoverage;
import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository.Candidate;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconcileAiefAgentOverlapUseCaseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Mock private OrganizationRepository organizations;
    @Mock private AiefOverlapRepository repository;

    private ReconcileAiefAgentOverlapUseCase useCase() {
        return new ReconcileAiefAgentOverlapUseCase(organizations, repository, AiefEditorTools.defaults());
    }

    private static Organization org(String id) {
        return new Organization(id, id, id, null, null, null, null, null, true, 0L, null, null);
    }

    @Test
    void should_deactivateAndReactivate_perOrganization() {
        when(organizations.findAllActive()).thenReturn(List.of(org("o1")));
        List<Candidate> cands = List.of(
                new Candidate("a", "u1", DAY, "Claude Code", true, null),
                new Candidate("b", "u2", DAY, "Codex CLI", false, "AGENT"));
        when(repository.findCandidates(eq("o1"), any(), eq(null))).thenReturn(cands);
        when(repository.findAgentCoverage(eq("o1"), eq(cands), any()))
                .thenReturn(Set.of(new AgentCoverage("u1", DAY, "claude-code")));
        when(repository.deactivate("o1", List.of("a"))).thenReturn(1L);
        when(repository.reactivate("o1", List.of("b"))).thenReturn(1L);

        var result = useCase().execute(null);

        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(result.reactivated()).isEqualTo(1);
    }

    @Test
    void should_continueWithOtherOrganizations_when_oneFails() {
        when(organizations.findAllActive()).thenReturn(List.of(org("o1"), org("o2")));
        when(repository.findCandidates(eq("o1"), any(), any())).thenThrow(new RuntimeException("boom"));
        List<Candidate> cands = List.of(new Candidate("a", "u1", DAY, "Claude Code", true, null));
        when(repository.findCandidates(eq("o2"), any(), any())).thenReturn(cands);
        when(repository.findAgentCoverage(eq("o2"), eq(cands), any()))
                .thenReturn(Set.of(new AgentCoverage("u1", DAY, "claude-code")));
        when(repository.deactivate("o2", List.of("a"))).thenReturn(1L);

        assertThat(useCase().execute(DAY).deactivated()).isEqualTo(1);
    }

    @Test
    void should_notWrite_when_nothingToChange() {
        when(organizations.findAllActive()).thenReturn(List.of(org("o1")));
        when(repository.findCandidates(eq("o1"), any(), any()))
                .thenReturn(List.of(new Candidate("a", "u1", DAY, "Claude Code", true, null)));
        when(repository.findAgentCoverage(eq("o1"), any(), any())).thenReturn(Set.of());

        useCase().execute(null);

        verify(repository, never()).deactivate(any(), any());
        verify(repository, never()).reactivate(any(), any());
    }
}
