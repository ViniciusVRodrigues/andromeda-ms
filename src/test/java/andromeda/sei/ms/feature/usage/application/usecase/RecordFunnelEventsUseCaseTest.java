package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.FunnelEventQuota;
import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventDraft;
import andromeda.sei.ms.feature.usage.domain.FunnelEventInvalidException;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import andromeda.sei.ms.feature.usage.domain.exception.FunnelEventsRateLimitedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RecordFunnelEventsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private static final String FUNNEL = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";

    @Mock private FunnelEventRepository repository;

    private RecordFunnelEventsUseCase useCase(boolean enabled, FunnelEventQuota quota) {
        return new RecordFunnelEventsUseCase(new TelemetrySettings(enabled), quota, repository);
    }

    private static List<FunnelEventDraft> drafts(int n) {
        return java.util.stream.IntStream.range(0, n)
                .mapToObj(i -> new FunnelEventDraft(FunnelStage.STEP_ENTER, NOW, "pessoal", null, null, "lg")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_storeAnonymousEvents() {
        useCase(true, new FunnelEventQuota()).execute(FUNNEL, FunnelFlow.SIGNUP, drafts(2), NOW);

        ArgumentCaptor<List<FunnelEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allSatisfy(e -> {
                    assertThat(e.getOrganizationId()).isNull();
                    assertThat(e.getUserId()).isNull();
                    assertThat(e.getFunnelId()).isEqualTo(FUNNEL);
                });
    }

    @Test
    void should_dropTheBatch_when_theCollectionIsOff() {
        useCase(false, new FunnelEventQuota()).execute(FUNNEL, FunnelFlow.SIGNUP, drafts(1), NOW);

        verifyNoInteractions(repository);
    }

    @Test
    void should_dropTheBatchSilently_when_theGlobalCeilingIsReached() {
        FunnelEventQuota quota = new FunnelEventQuota();
        for (int i = 0; i < 250; i++) quota.admit("other-" + i, 20);

        useCase(true, quota).execute(FUNNEL, FunnelFlow.SIGNUP, drafts(1), NOW);

        verifyNoInteractions(repository);
    }

    @Test
    void should_answer429WithRetryAfter_when_theFunnelExceeds200EventsPerDay() {
        FunnelEventQuota quota = new FunnelEventQuota();
        RecordFunnelEventsUseCase uc = useCase(true, quota);
        for (int i = 0; i < 10; i++) uc.execute(FUNNEL, FunnelFlow.SIGNUP, drafts(20), NOW);

        assertThatThrownBy(() -> uc.execute(FUNNEL, FunnelFlow.SIGNUP, drafts(1), NOW))
                .isInstanceOfSatisfying(FunnelEventsRateLimitedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("FUNNEL_EVENTS_RATE_LIMITED");
                    assertThat(e.getRetryAfterSeconds()).isPositive();
                });
    }

    @Test
    void should_writeNothing_when_oneEventIsIncoherent() {
        List<FunnelEventDraft> mixed = List.of(drafts(1).get(0),
                new FunnelEventDraft(FunnelStage.COMPLETE, NOW, "confirmar", null, null, null));

        assertThatThrownBy(() -> useCase(true, new FunnelEventQuota()).execute(FUNNEL, FunnelFlow.SIGNUP, mixed, NOW))
                .isInstanceOf(FunnelEventInvalidException.class);
        verifyNoInteractions(repository);
    }
}
