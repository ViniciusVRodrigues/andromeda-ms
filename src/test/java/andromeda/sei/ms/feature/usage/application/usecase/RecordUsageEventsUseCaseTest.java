package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventDraft;
import andromeda.sei.ms.feature.usage.domain.UsageEventInvalidException;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RecordUsageEventsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private static final String SESSION = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";
    private static final String VIEW = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    @Mock private UsageEventRepository repository;

    private RecordUsageEventsUseCase useCase(boolean enabled) {
        return new RecordUsageEventsUseCase(new TelemetrySettings(enabled), repository);
    }

    private static UsageEventDraft pageView(String screen) {
        return UsageEventDraft.builder(UsageEventType.PAGE_VIEW, NOW).sessionId(SESSION).viewId(VIEW).screen(screen).build();
    }

    @SuppressWarnings("unchecked")
    private List<UsageEvent> saved() {
        ArgumentCaptor<List<UsageEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_storeEveryEvent_withTheIdentityPassedByTheCaller() {
        useCase(true).execute("org-A", "user-1", List.of(pageView("backlog"), pageView("projetos"), pageView("perfil")), NOW);

        assertThat(saved()).hasSize(3)
                .allSatisfy(e -> {
                    assertThat(e.getOrganizationId()).isEqualTo("org-A");
                    assertThat(e.getUserId()).isEqualTo("user-1");
                });
    }

    @Test
    void should_storeEachOrganizationWithItsOwnId() {
        useCase(true).execute("org-A", "user-1", List.of(pageView("backlog")), NOW);
        useCase(true).execute("org-B", "user-2", List.of(pageView("backlog")), NOW);

        ArgumentCaptor<List<UsageEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, org.mockito.Mockito.times(2)).insertAll(captor.capture());
        assertThat(captor.getAllValues().get(0).get(0).getOrganizationId()).isEqualTo("org-A");
        assertThat(captor.getAllValues().get(1).get(0).getOrganizationId()).isEqualTo("org-B");
    }

    @Test
    void should_storeNothing_when_telemetryIsOff() {
        useCase(false).execute("org-A", "user-1", List.of(pageView("backlog")), NOW);

        verifyNoInteractions(repository);
    }

    @Test
    void should_rejectTheWholeBatch_andStoreNothing_when_oneEventIsIncoherent() {
        UsageEventDraft screenTimeWithoutDuration = UsageEventDraft.builder(UsageEventType.SCREEN_TIME, NOW)
                .sessionId(SESSION).viewId(VIEW).screen("backlog").build();

        assertThatThrownBy(() -> useCase(true).execute("org-A", "user-1",
                List.of(pageView("backlog"), screenTimeWithoutDuration), NOW))
                .isInstanceOf(UsageEventInvalidException.class)
                .extracting("code").isEqualTo("USAGE_EVENT_INVALID");
        verify(repository, never()).insertAll(anyList());
    }
}
