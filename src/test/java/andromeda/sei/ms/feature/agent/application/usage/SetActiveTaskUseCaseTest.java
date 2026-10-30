package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRequestInvalidException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTaskNotFoundException;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.usecase.RegisterAgentAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetActiveTaskUseCaseTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final String DEVICE = "dev-1";
    static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
    ResolveAgentTaskUseCase resolveTask = mock(ResolveAgentTaskUseCase.class);
    AgentUsageQueries queries = mock(AgentUsageQueries.class);
    RegisterAgentAllocationUseCase register = mock(RegisterAgentAllocationUseCase.class);
    AgentDevice device = new AgentDevice();
    SetActiveTaskUseCase useCase;
    AgentPrincipal principal = new AgentPrincipal(ORG, USER, DEVICE);

    @BeforeEach
    void setUp() {
        device.setId(DEVICE);
        device.setOrganizationId(ORG);
        device.setUserId(USER);
        device.setTimezone("America/Sao_Paulo");
        device.setPairedAt(Instant.parse("2026-10-01T00:00:00Z"));
        when(devices.findActiveByIdAndOrganizationId(DEVICE, ORG)).thenReturn(Optional.of(device));
        when(devices.findDevicesOfUser(ORG, USER)).thenReturn(List.of(device));
        when(resolveTask.resolveRef(ORG, USER, "AB#123")).thenReturn(Optional.of(
                new ResolvedTask("wi-123", "proj-1", "Task 123", "AB#123", AttributionSource.TASK_REF)));
        useCase = new SetActiveTaskUseCase(devices, resolveTask, new PairingRowDispositionPolicy(), queries, register,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static AIUsage row(String id, String repo, Instant bucket) {
        AIUsage u = new AIUsage(id, ORG, USER, null, "agent", TODAY, "m", "agent:x", repo, DEVICE, "NOTE", 1, 2, 1,
                "claude-code", null, null, NOW, true, 0L, NOW, NOW);
        u.setDeviceId(DEVICE);
        u.setRepository(repo);
        u.setBucketStart(bucket);
        u.setBranch("feature/x");
        return u;
    }

    @Test
    void should_storeTheTaskAndAllocateOnlyTheUnallocatedLiveRowsOfTheRepo_when_theTaskIsSet() {
        Instant live = Instant.parse("2026-10-29T14:00:00Z");
        Instant historic = Instant.parse("2026-09-30T14:00:00Z");
        when(queries.findUnallocatedAgentRows(ORG, USER, TODAY)).thenReturn(List.of(
                row("r1", "github.com/acme/andromeda-ms", live),
                row("r2", "https://github.com/Acme/andromeda-ms.git", live),
                row("r3", "github.com/acme/other", live),
                row("r4", null, live),
                row("r5", "github.com/acme/andromeda-ms", historic)));

        SetActiveTaskUseCase.Output out = useCase.execute(principal, new SetActiveTaskUseCase.Input(
                " AB#123 ", null, TODAY, "github.com/acme/andromeda-ms"));

        assertThat(out.retroactiveRows()).isEqualTo(2);
        assertThat(out.workItemId()).isEqualTo("wi-123");
        ArgumentCaptor<AgentDevice.ActiveTask> task = ArgumentCaptor.forClass(AgentDevice.ActiveTask.class);
        verify(devices).setActiveTask(eq(DEVICE), eq(ORG), task.capture());
        assertThat(task.getValue().workItemId()).isEqualTo("wi-123");
        assertThat(task.getValue().since()).isEqualTo(NOW);
        ArgumentCaptor<RegisterAgentAllocationUseCase.Registration> reg =
                ArgumentCaptor.forClass(RegisterAgentAllocationUseCase.Registration.class);
        verify(register).execute(reg.capture());
        assertThat(reg.getValue().usageIds()).containsExactly("r1", "r2");
        assertThat(reg.getValue().day()).isEqualTo(TODAY);
        assertThat(reg.getValue().resolvedBy()).isEqualTo(AiUsageAllocation.ResolvedBy.MARK);
        verify(queries).stampTask(eq(ORG), eq(List.of("r1", "r2")), eq("wi-123"), eq("proj-1"), eq("AB#123"),
                eq("ACTIVE_TASK"));
    }

    @Test
    void should_storeTheBranchAndRetroactOnlyRowsOfThatBranch_when_theBranchIsGiven() {
        Instant live = Instant.parse("2026-10-29T14:00:00Z");
        AIUsage other = row("r2", "github.com/acme/a", live);
        other.setBranch("feature/outra");
        AIUsage noBranch = row("r3", "github.com/acme/a", live);
        noBranch.setBranch(null);
        when(queries.findUnallocatedAgentRows(ORG, USER, TODAY)).thenReturn(List.of(
                row("r1", "github.com/acme/a", live), other, noBranch));

        SetActiveTaskUseCase.Output out = useCase.execute(principal, new SetActiveTaskUseCase.Input(
                "AB#123", null, TODAY, "github.com/acme/a", " feature/x "));

        assertThat(out.retroactiveRows()).isEqualTo(2);
        ArgumentCaptor<AgentDevice.ActiveTask> task = ArgumentCaptor.forClass(AgentDevice.ActiveTask.class);
        verify(devices).setActiveTask(eq(DEVICE), eq(ORG), task.capture());
        assertThat(task.getValue().branch()).isEqualTo("feature/x");
        assertThat(task.getValue().day()).isEqualTo(TODAY);
        ArgumentCaptor<RegisterAgentAllocationUseCase.Registration> reg =
                ArgumentCaptor.forClass(RegisterAgentAllocationUseCase.Registration.class);
        verify(register).execute(reg.capture());
        assertThat(reg.getValue().usageIds()).containsExactly("r1", "r3");
    }

    @Test
    void should_allocateEveryRepo_when_noRepoIsGiven() {
        Instant live = Instant.parse("2026-10-29T14:00:00Z");
        when(queries.findUnallocatedAgentRows(ORG, USER, TODAY)).thenReturn(List.of(
                row("r1", "github.com/acme/a", live), row("r2", "github.com/acme/b", live)));

        SetActiveTaskUseCase.Output out = useCase.execute(principal,
                new SetActiveTaskUseCase.Input("AB#123", Instant.parse("2026-10-29T13:00:00Z"), TODAY, null));

        assertThat(out.retroactiveRows()).isEqualTo(2);
    }

    @Test
    void should_acceptYesterday_and_notCallTheRegisterWithoutRows() {
        when(queries.findUnallocatedAgentRows(ORG, USER, TODAY.minusDays(1))).thenReturn(List.of());

        SetActiveTaskUseCase.Output out = useCase.execute(principal,
                new SetActiveTaskUseCase.Input("AB#123", null, TODAY.minusDays(1), null));

        assertThat(out.retroactiveRows()).isZero();
        verify(register, never()).execute(any());
    }

    @Test
    void should_failWithRequestInvalid_when_theDayIsNotTodayOrYesterday() {
        assertThatThrownBy(() -> useCase.execute(principal,
                new SetActiveTaskUseCase.Input("AB#123", null, TODAY.minusDays(2), null)))
                .isInstanceOf(AgentRequestInvalidException.class);
        assertThatThrownBy(() -> useCase.execute(principal,
                new SetActiveTaskUseCase.Input("AB#123", null, TODAY.plusDays(1), null)))
                .isInstanceOf(AgentRequestInvalidException.class);
        verify(devices, never()).setActiveTask(any(), any(), any());
    }

    @Test
    void should_fail422_when_theTaskIsNotFound() {
        when(resolveTask.resolveRef(ORG, USER, "AB#999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(principal,
                new SetActiveTaskUseCase.Input("AB#999", null, TODAY, null)))
                .isInstanceOf(AgentTaskNotFoundException.class);
        verify(devices, never()).setActiveTask(any(), any(), any());
        verify(register, never()).execute(any());
    }

    @Test
    void should_failWithRequestInvalid_when_theTaskRefIsBlank() {
        assertThatThrownBy(() -> useCase.execute(principal,
                new SetActiveTaskUseCase.Input("  ", null, TODAY, null)))
                .isInstanceOf(AgentRequestInvalidException.class);
    }

    @Test
    void should_clearTheTask_when_clearIsCalled() {
        new ClearActiveTaskUseCase(devices).execute(principal);

        verify(devices).setActiveTask(DEVICE, ORG, null);
    }
}
