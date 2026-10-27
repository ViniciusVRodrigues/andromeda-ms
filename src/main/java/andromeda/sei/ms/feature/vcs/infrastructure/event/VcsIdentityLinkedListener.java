package andromeda.sei.ms.feature.vcs.infrastructure.event;

import andromeda.sei.ms.feature.vcs.application.usecase.ReattributeVcsAuthorshipUseCase;
import andromeda.sei.ms.shared.application.security.VcsIdentityLinked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs the retroactive attribution in background when an identity is linked (atribuir-historico-vcs
 * D1). The link has already answered; a failure here is only logged, with ids and no personal data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VcsIdentityLinkedListener {

    private final ReattributeVcsAuthorshipUseCase reattributeUseCase;

    @Async
    @EventListener
    public void on(VcsIdentityLinked event) {
        try {
            reattributeUseCase.execute(event.organizationId(), event.userId());
        } catch (RuntimeException e) {
            log.warn("VCS history attribution failed: org={} userId={}: {}", event.organizationId(), event.userId(),
                    e.getClass().getSimpleName());
        }
    }
}
