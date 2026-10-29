package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ApprovedDeviceOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Day the agent takes over from AIEF (agente-endpoint-v2, design 4.2): approval moves it and supersedes AIEF rows. */
class AiefCutoverTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    // 2026-10-29 12:00Z is 09:00 in Sao Paulo, same day
    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    FakeAgentStore store;
    MutableClock clock;
    AiUsageAllocationRepository allocations;
    AgentUsageQueries queries;
    DefaultAiefCutover cutover;
    RequestDeviceCodeUseCase request;
    ApprovePairingUseCase approve;

    @BeforeEach
    void setUp() {
        store = new FakeAgentStore();
        clock = new MutableClock(NOW);
        allocations = mock(AiUsageAllocationRepository.class);
        queries = mock(AgentUsageQueries.class);
        cutover = new DefaultAiefCutover(allocations);
        request = new RequestDeviceCodeUseCase(store, new AgentVersionPolicy("0.1.0", "0.2.0"),
                Duration.ofMinutes(10), 5, clock);
        approve = new ApprovePairingUseCase(store, store, cutover, clock);
    }

    private DeviceCodeOutput ask() {
        return request.execute(new DeviceCodeInput("0.2.0", "NOTE", "windows", "11", "Windows 11", "x64",
                "America/Sao_Paulo", "1.2.3.4"));
    }

    @Test
    void should_startToday_and_notTouchAief_when_theDayIsNotValidated() {
        when(allocations.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, USER, LocalDate.of(2026, 10, 29)))
                .thenReturn(false);

        ApprovedDeviceOutput approved = approve.execute(ORG, USER, ask().userCode());

        AgentDevice d = store.devices.get(approved.deviceId());
        assertThat(d.getCutoverDay()).isEqualTo(LocalDate.of(2026, 10, 29));
        org.mockito.Mockito.verifyNoInteractions(queries);
    }

    @Test
    void should_startTomorrow_when_todayHasAValidatedAiefAllocation() {
        when(allocations.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, USER, LocalDate.of(2026, 10, 29)))
                .thenReturn(true);

        ApprovedDeviceOutput approved = approve.execute(ORG, USER, ask().userCode());

        assertThat(store.devices.get(approved.deviceId()).getCutoverDay()).isEqualTo(LocalDate.of(2026, 10, 30));
        org.mockito.Mockito.verifyNoInteractions(queries);
    }

    @Test
    void should_neverDeactivateAief_when_theApprovalIsRepeated() {
        String code = ask().userCode();
        approve.execute(ORG, USER, code);
        org.mockito.Mockito.clearInvocations(queries);

        approve.execute(ORG, USER, code);

        org.mockito.Mockito.verifyNoInteractions(queries);
    }
}
