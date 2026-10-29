package andromeda.sei.ms.feature.organization.infrastructure.scheduling;

import andromeda.sei.ms.feature.agent.application.event.FirstAgentDeviceApproved;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase.Trigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs the pattern detection in the background when the first agent device of an organization is
 * approved (design 14.5). A failure is only logged: it never reaches the approval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstAgentDeviceDetectionListener {

    private final DetectWorkItemPatternsUseCase detectUseCase;

    @Async
    @EventListener
    public void on(FirstAgentDeviceApproved event) {
        try {
            detectUseCase.execute(event.organizationId(), Trigger.FIRST_AGENT_DEVICE);
        } catch (Exception e) {
            log.warn("Detecção de padrões após o primeiro dispositivo falhou para a organização {}",
                    event.organizationId(), e);
        }
    }
}
