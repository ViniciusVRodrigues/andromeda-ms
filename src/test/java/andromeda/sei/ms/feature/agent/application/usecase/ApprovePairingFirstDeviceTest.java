package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceCodeInput;
import andromeda.sei.ms.feature.agent.application.event.FirstAgentDeviceApproved;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The first approved device of an organization triggers the pattern detection (event); later ones do not. */
class ApprovePairingFirstDeviceTest {

    private FakeAgentStore store;
    private MutableClock clock;
    private RequestDeviceCodeUseCase request;
    private ApplicationEventPublisher events;
    private ApprovePairingUseCase approve;

    @BeforeEach
    void setUp() {
        store = new FakeAgentStore();
        clock = new MutableClock(Instant.parse("2026-10-29T12:00:00Z"));
        events = mock(ApplicationEventPublisher.class);
        request = new RequestDeviceCodeUseCase(store, new AgentVersionPolicy("0.1.0", "0.2.0"),
                Duration.ofMinutes(10), 5, clock);
        approve = new ApprovePairingUseCase(store, store, AiefCutover.NONE, events, clock);
    }

    private String pending() {
        return request.execute(new DeviceCodeInput("0.2.0", "NOTE", "windows", "11", "Windows 11", "x64",
                "America/Sao_Paulo", "1.2.3.4")).userCode();
    }

    @Test
    void should_publishTheEvent_when_theFirstDeviceOfTheOrganizationIsApproved() {
        approve.execute("org-1", "user-1", pending());

        verify(events).publishEvent(new FirstAgentDeviceApproved("org-1"));
    }

    @Test
    void should_notPublish_forTheSecondDeviceOfTheSameOrganization() {
        approve.execute("org-1", "user-1", pending());
        approve.execute("org-1", "user-2", pending());

        verify(events, times(1)).publishEvent(any(FirstAgentDeviceApproved.class));
    }

    @Test
    void should_publishAgain_forTheFirstDeviceOfAnotherOrganization() {
        approve.execute("org-1", "user-1", pending());
        approve.execute("org-2", "user-9", pending());

        verify(events).publishEvent(new FirstAgentDeviceApproved("org-2"));
    }

    @Test
    void should_stillApprove_when_publishingTheEventFails() {
        doThrow(new IllegalStateException("boom")).when(events).publishEvent(any(FirstAgentDeviceApproved.class));

        var output = approve.execute("org-1", "user-1", pending());

        assertThat(output.deviceId()).isNotNull();
        assertThat(store.devices).hasSize(1);
    }

    @Test
    void should_notPublish_whenApprovalIsRepeatedForTheSamePairing() {
        String code = pending();
        approve.execute("org-1", "user-1", code);
        approve.execute("org-1", "user-1", code);

        verify(events, times(1)).publishEvent(any(FirstAgentDeviceApproved.class));
        verify(events, never()).publishEvent(new FirstAgentDeviceApproved("org-2"));
    }
}
