package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LinkFunnelUseCaseTest {

    private static final String FUNNEL = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";

    @Mock private FunnelEventRepository repository;

    private LinkFunnelUseCase useCase(boolean enabled) {
        return new LinkFunnelUseCase(repository, new TelemetrySettings(enabled));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_linkTheLast7DaysAndRecordComplete() {
        Instant before = Instant.now();

        useCase(true).execute(FUNNEL, FunnelFlow.SIGNUP, "org-1", "user-1");

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(repository).linkToAccount(eq(FUNNEL), eq(FunnelFlow.SIGNUP), eq("org-1"), eq("user-1"),
                since.capture(), any(Instant.class));
        assertThat(Duration.between(since.getValue(), before).abs()).isBetween(Duration.ofDays(7).minusSeconds(5),
                Duration.ofDays(7).plusSeconds(5));
        ArgumentCaptor<List<FunnelEvent>> complete = ArgumentCaptor.forClass(List.class);
        verify(repository).insertAll(complete.capture());
        FunnelEvent c = complete.getValue().get(0);
        assertThat(c.getStage()).isEqualTo(FunnelStage.COMPLETE);
        assertThat(c.getFlow()).isEqualTo(FunnelFlow.SIGNUP);
        assertThat(c.getOrganizationId()).isEqualTo("org-1");
        assertThat(c.getUserId()).isEqualTo("user-1");
    }

    @Test
    void should_ignoreAnAbsentOrMalformedFunnelId() {
        LinkFunnelUseCase uc = useCase(true);

        uc.execute(null, FunnelFlow.SIGNUP, "org-1", "user-1");
        uc.execute("abc", FunnelFlow.SIGNUP, "org-1", "user-1");
        uc.execute("3f2b1c4e-5a6d-1e7f-8a9b-0c1d2e3f4a5b", FunnelFlow.INVITE, "org-1", "user-1"); // not v4

        verifyNoInteractions(repository);
    }

    @Test
    void should_doNothing_when_theCollectionIsOff() {
        useCase(false).execute(FUNNEL, FunnelFlow.INVITE, "org-1", "user-1");

        verifyNoInteractions(repository);
    }

    @Test
    void should_swallowARepositoryFailure() {
        doThrow(new IllegalStateException("mongo down")).when(repository)
                .linkToAccount(any(), any(), any(), any(), any(), any());

        useCase(true).execute(FUNNEL, FunnelFlow.SIGNUP, "org-1", "user-1"); // must not throw
    }

    @Test
    void should_swallowAFailureWhileRecordingComplete() {
        doThrow(new IllegalStateException("mongo down")).when(repository).insertAll(anyList());

        useCase(true).execute(FUNNEL, FunnelFlow.INVITE, "org-1", "user-1"); // must not throw
    }
}
