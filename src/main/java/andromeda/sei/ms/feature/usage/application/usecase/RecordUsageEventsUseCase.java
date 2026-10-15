package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventDraft;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Validates and stores a batch of client events. Identity is a parameter, taken by the controller
 * from the token and the tenant, never from the body. All-or-nothing: one incoherent event rejects
 * the batch (422) before anything is written.
 */
@Component
@RequiredArgsConstructor
public class RecordUsageEventsUseCase {

    private final TelemetrySettings settings;
    private final UsageEventRepository repository;

    public void execute(String organizationId, String userId, List<UsageEventDraft> drafts) {
        execute(organizationId, userId, drafts, Instant.now());
    }

    void execute(String organizationId, String userId, List<UsageEventDraft> drafts, Instant receivedAt) {
        if (!settings.isEnabled()) return;
        List<UsageEvent> events = drafts.stream()
                .map(d -> UsageEvent.fromClient(organizationId, userId, d, receivedAt))
                .toList();
        repository.insertAll(events);
    }
}
