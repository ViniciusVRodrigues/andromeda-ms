package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.AgentAttribution;
import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.aiusage.application.usecase.CalculateAiCostUseCase;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.RegisterAgentAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static andromeda.sei.ms.feature.agent.application.usage.IngestAgentUsageUseCaseTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The mandatory scenario of agente-endpoint-v2 (design 15): the person moves a row from #123 to #200, the
 * agent resends the same key with more output and later {@code andromeda task AB#123} runs the same day.
 * The row has to stay in the MANUAL allocation of #200, with the new numbers and without the AGENT seal.
 */
class ManualCorrectionWinsTest {

    /** Fake of the Mongo queries over the rows of {@link InMemoryUsages}. */
    static class FakeQueries implements AgentUsageQueries {
        final InMemoryUsages usages;

        FakeQueries(InMemoryUsages usages) {
            this.usages = usages;
        }

        @Override
        public boolean existsActiveAief(String o, String u, LocalDate day, Collection<String> editors) {
            return false;
        }

        @Override
        public boolean existsAiefInValidatedAllocation(String o, String u, LocalDate day, Collection<String> editors) {
            return false;
        }

        @Override
        public long supersedeAief(String o, String u, LocalDate day, Collection<String> editors) {
            return 0;
        }

        @Override
        public boolean existsActiveAgentRow(String o, String u, LocalDate day, String tool) {
            return false;
        }

        @Override
        public java.util.Set<String> agentModelsOfDay(String o, String u, LocalDate day) {
            return java.util.Set.of();
        }

        @Override
        public List<AIUsage> findUnallocatedAgentRows(String o, String u, LocalDate day) {
            return usages.byKey.values().stream()
                    .filter(r -> r.isActive() && day.equals(r.getDay()) && r.getAllocationId() == null).toList();
        }

        @Override
        public List<AIUsage> findActiveAgentRowsSince(String o, String u, Instant since) {
            return usages.byKey.values().stream().filter(AIUsage::isActive).toList();
        }

        @Override
        public void stampTask(String o, Collection<String> ids, String workItemId, String projectId, String taskRef,
                              String attributionSource) {
            usages.byKey.values().stream().filter(r -> ids.contains(r.getId())).forEach(r -> {
                r.setWorkItemId(workItemId);
                r.setTaskRef(taskRef);
                r.setAttributionSource(attributionSource);
            });
        }
    }

    @Test
    void should_keepTheMovedRowInTheManualAllocationWithTheNewNumbers_when_theAgentResendsAndTheTaskIsSetTheSameDay() {
        AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
        InMemoryUsages usages = new InMemoryUsages();
        InMemoryBatches batches = new InMemoryBatches();
        ResolveAgentTaskUseCase resolveTask = mock(ResolveAgentTaskUseCase.class);
        RegisterAgentAllocationUseCase registerAllocation = mock(RegisterAgentAllocationUseCase.class);
        AutoAssignAllocationUseCase autoAssign = mock(AutoAssignAllocationUseCase.class);
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        AiUsageAllocationRepository allocations = mock(AiUsageAllocationRepository.class);
        AgentDevice device = new AgentDevice();
        device.setId(DEVICE);
        device.setOrganizationId(ORG);
        device.setUserId(USER);
        device.setTimezone("America/Sao_Paulo");
        device.setPairedAt(Instant.parse("2026-10-01T00:00:00Z"));
        when(devices.findActiveByIdAndOrganizationId(DEVICE, ORG)).thenReturn(Optional.of(device));
        when(devices.findDevicesOfUser(ORG, USER)).thenReturn(List.of(device));
        when(resolveTask.execute(any())).thenReturn(new AgentAttribution(null, null, AttributionSource.NONE));
        FakeQueries queries = new FakeQueries(usages);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        IngestAgentUsageUseCase ingest = new IngestAgentUsageUseCase(devices, batches, usages, resolveTask,
                new PairingRowDispositionPolicy(), registerAllocation, autoAssign, new CalculateAiCostUseCase(),
                organizations, Duration.ofDays(90), Duration.ofDays(7), false, clock);
        AgentPrincipal principal = new AgentPrincipal(ORG, USER, DEVICE);

        // the agent sent the row and it was allocated to #123 as AGENT
        ingest.execute(principal, batch(BATCH, row("k1", 10, 3400)));
        AIUsage stored = usages.byKey.get("k1");
        stored.setAllocationId("alloc-agent");
        stored.setWorkItemId("wi-123");
        stored.setTaskRef("AB#123");
        stored.setAttributionSource("TASK_REF");
        AiUsageAllocation agentAllocation = new AiUsageAllocation("alloc-agent", ORG, USER, LocalDate.of(2026, 10, 29),
                new ArrayList<>(List.of(stored.getId())),
                List.of(new AllocationSplit("wi-123", "Task 123", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AGENT, true, 0L, NOW, NOW);
        when(allocations.findById("alloc-agent", ORG)).thenReturn(Optional.of(agentAllocation));
        when(allocations.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            if (a.getId() == null) a.setId("alloc-manual");
            return a;
        });
        doAnswer(inv -> {
            List<String> ids = inv.getArgument(0);
            usages.byKey.values().stream().filter(r -> ids.contains(r.getId()))
                    .forEach(r -> r.setAllocationId(inv.getArgument(1)));
            return null;
        }).when(allocations).updateAllocationIdOnUsageRecords(anyList(), anyString());
        ResolveAgentTaskUseCase mover = mock(ResolveAgentTaskUseCase.class);
        when(mover.resolveItem(ORG, USER, "wi-200")).thenReturn(Optional.of(
                new ResolvedTask("wi-200", "proj-1", "Task 200", "AB#200", AttributionSource.TASK_REF)));
        MoveAgentUsageUseCase move = new MoveAgentUsageUseCase(allocations, mover, queries,
                mock(CalculateAiRatiosUseCase.class), mock(WorkItemRepository.class));

        // 1. the person moves the row to #200
        MoveAgentUsageUseCase.MoveResult moved = move.execute(ORG, USER, "alloc-agent", List.of(stored.getId()), "wi-200");
        assertThat(moved.target().assignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.MANUAL);

        // 2. the agent resends the same key with more output
        ingest.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002", row("k1", 10, 5100)));

        // 3. andromeda task AB#123 on the same day
        clearInvocations(registerAllocation);
        when(resolveTask.resolveRef(ORG, USER, "AB#123")).thenReturn(Optional.of(
                new ResolvedTask("wi-123", "proj-1", "Task 123", "AB#123", AttributionSource.ACTIVE_TASK)));
        SetActiveTaskUseCase setActive = new SetActiveTaskUseCase(devices, resolveTask,
                new PairingRowDispositionPolicy(), queries, registerAllocation, clock);
        SetActiveTaskUseCase.Output out = setActive.execute(principal,
                new SetActiveTaskUseCase.Input("AB#123", null, LocalDate.of(2026, 10, 29), null));

        assertThat(out.retroactiveRows()).isZero();
        verify(registerAllocation, never()).execute(any());
        AIUsage finalRow = usages.byKey.get("k1");
        assertThat(finalRow.getAllocationId()).isEqualTo("alloc-manual");
        assertThat(finalRow.getOutputTokens()).isEqualTo(5100);
        assertThat(finalRow.getWorkItemId()).isEqualTo("wi-200");
        assertThat(finalRow.getAttributionSource()).isEqualTo("MANUAL");
        assertThat(moved.target().assignedBy()).isNotEqualTo(AiUsageAllocation.AssignedBy.AGENT);
        assertThat(moved.target().movedFromAgent()).isTrue();
    }
}
