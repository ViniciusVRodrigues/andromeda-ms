package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAgentTodayUseCaseTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

    AgentUsageQueries queries = mock(AgentUsageQueries.class);
    AgentDeviceRepository devices = mock(AgentDeviceRepository.class);
    WorkItemRepository workItems = mock(WorkItemRepository.class);
    AgentDevice device = new AgentDevice();
    GetAgentTodayUseCase useCase;

    @BeforeEach
    void setUp() {
        device.setId("dev-1");
        device.setOrganizationId(ORG);
        device.setUserId(USER);
        device.setMachineName("NOTE");
        device.setTimezone("America/Sao_Paulo");
        device.setPairedAt(Instant.parse("2026-10-01T00:00:00Z"));
        device.setActiveTask(new AgentDevice.ActiveTask("wi-1", "AB#123", "Task", NOW, null, TODAY));
        when(devices.findDevicesOfUser(ORG, USER)).thenReturn(List.of(device));
        WorkItem item = mock(WorkItem.class);
        when(item.getSource()).thenReturn(WorkItem.Source.AZURE_DEVOPS);
        when(item.getExternalId()).thenReturn("123");
        when(item.getTitle()).thenReturn("Task 123");
        when(workItems.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(item));
        when(workItems.findByIdAndOrganizationIdAndActiveTrue("wi-j", ORG)).thenReturn(Optional.empty());
        useCase = new GetAgentTodayUseCase(queries, devices, new PairingRowDispositionPolicy(), workItems,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static AIUsage row(String id, String repo, String branch, String workItemId, String source, boolean approx,
                       String hour, long out) {
        AIUsage u = new AIUsage(id, ORG, USER, null, "agent", TODAY, "m", "agent:x", repo, "dev-1", "NOTE", 1, out, 1,
                "claude-code", null, null, NOW, true, 0L, NOW, NOW);
        u.setDeviceId("dev-1");
        u.setRepository(repo);
        u.setBucketStart(Instant.parse("2026-10-29T" + hour + ":00:00Z"));
        u.setBranch(branch);
        u.setWorkItemId(workItemId);
        u.setAttributionSource(source);
        u.setBranchApproximate(approx);
        return u;
    }

    @Test
    void should_groupByRepoBranchAndTask_and_markBranchApproximate_and_listUnassigned() {
        when(queries.findActiveAgentRowsSince(any(), any(), any())).thenReturn(List.of(
                row("r1", "github.com/acme/a", "feature/x", "wi-1", "BRANCH", false, "12", 10),
                row("r2", "github.com/acme/a", "feature/x", "wi-1", "ACTIVE_TASK", false, "13", 20),
                row("r3", "github.com/acme/b", "main", "wi-1", "BRANCH", true, "14", 5),
                row("r4", "github.com/acme/c", null, null, null, false, "14", 7),
                row("r5", "github.com/acme/c", null, null, null, false, "14", 8),
                // before the pairing: not shown as unassigned
                row("r6", "github.com/acme/c", null, null, null, false, "00", 99)));
        device.setPairedAt(Instant.parse("2026-10-29T10:00:00Z"));

        GetAgentTodayUseCase.Today today = useCase.execute(ORG, USER);

        assertThat(today.resolved()).hasSize(2);
        GetAgentTodayUseCase.ResolvedGroup a = today.resolved().stream()
                .filter(g -> g.repo().endsWith("/a")).findFirst().orElseThrow();
        assertThat(a.resolvedBy()).isEqualTo("MARK");
        assertThat(a.branchApproximate()).isFalse();
        assertThat(a.workItem().displayId()).isEqualTo("AB#123");
        assertThat(a.workItem().title()).isEqualTo("Task 123");
        assertThat(a.since()).isEqualTo(Instant.parse("2026-10-29T12:00:00Z"));
        GetAgentTodayUseCase.ResolvedGroup b = today.resolved().stream()
                .filter(g -> g.repo().endsWith("/b")).findFirst().orElseThrow();
        assertThat(b.branchApproximate()).isTrue();
        assertThat(b.resolvedBy()).isEqualTo("BRANCH");
        assertThat(today.unassigned()).hasSize(1);
        assertThat(today.unassigned().get(0).rows()).isEqualTo(2);
        assertThat(today.unassigned().get(0).outputTokens()).isEqualTo(15);
        assertThat(today.devices()).hasSize(1);
        assertThat(today.devices().get(0).activeTask().ref()).isEqualTo("AB#123");
    }

    @Test
    void should_returnOnlyTheDevices_when_thereAreNoRows() {
        when(queries.findActiveAgentRowsSince(any(), any(), any())).thenReturn(List.of());

        GetAgentTodayUseCase.Today today = useCase.execute(ORG, USER);

        assertThat(today.resolved()).isEmpty();
        assertThat(today.unassigned()).isEmpty();
        assertThat(today.devices()).hasSize(1);
    }

    @Test
    void should_skipRevokedDevices_and_pickTheStrongestResolution() {
        AgentDevice revoked = new AgentDevice();
        revoked.setId("dev-2");
        revoked.revoke(USER, NOW);
        when(devices.findDevicesOfUser(ORG, USER)).thenReturn(List.of(device, revoked));
        when(queries.findActiveAgentRowsSince(any(), any(), any())).thenReturn(List.of(
                row("r1", "github.com/acme/a", "m", "wi-1", "SINGLE_ACTIVE", false, "12", 1),
                row("r2", "github.com/acme/a", "m", "wi-1", "BRANCH", false, "12", 1)));

        GetAgentTodayUseCase.Today today = useCase.execute(ORG, USER);

        assertThat(today.devices()).hasSize(1);
        assertThat(today.resolved().get(0).resolvedBy()).isEqualTo("BRANCH");
    }
}
