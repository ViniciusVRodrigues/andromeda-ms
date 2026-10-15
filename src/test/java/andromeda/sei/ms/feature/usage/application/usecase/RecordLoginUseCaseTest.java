package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.usage.application.TelemetrySettings;
import andromeda.sei.ms.feature.usage.domain.LoginMethod;
import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordLoginUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private UsageEventRepository usageEventRepository;

    private RecordLoginUseCase useCase(boolean enabled) {
        return new RecordLoginUseCase(userRepository, usageEventRepository, new TelemetrySettings(enabled));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_recordLastLoginAndLoginEvent() {
        useCase(true).execute("user-1", "org-A", LoginMethod.PASSWORD);

        verify(userRepository).recordLogin(eq("user-1"), eq("org-A"), any(Instant.class));
        ArgumentCaptor<List<UsageEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(usageEventRepository).insertAll(captor.capture());
        UsageEvent event = captor.getValue().get(0);
        assertThat(event.getType()).isEqualTo(UsageEventType.LOGIN);
        assertThat(event.getValue()).isEqualTo("password");
        assertThat(event.getOrganizationId()).isEqualTo("org-A");
        assertThat(event.getUserId()).isEqualTo("user-1");
    }

    @Test
    void should_recordLastLoginButNoEvent_when_telemetryIsOff() {
        useCase(false).execute("user-1", "org-A", LoginMethod.INVITE);

        verify(userRepository).recordLogin(eq("user-1"), eq("org-A"), any(Instant.class));
        verify(usageEventRepository, never()).insertAll(anyList());
    }

    @Test
    void should_notThrow_when_recordLoginFails_andStillTryTheEvent() {
        doThrow(new IllegalStateException("mongo down joao@x.com")).when(userRepository)
                .recordLogin(any(), any(), any());

        assertThatCode(() -> useCase(true).execute("user-1", "org-A", LoginMethod.SIGNUP)).doesNotThrowAnyException();
        verify(usageEventRepository).insertAll(anyList());
    }

    @Test
    void should_notThrow_when_eventInsertFails() {
        doThrow(new IllegalStateException("mongo down")).when(usageEventRepository).insertAll(anyList());

        assertThatCode(() -> useCase(true).execute("user-1", "org-A", LoginMethod.PASSWORD)).doesNotThrowAnyException();
        verify(userRepository).recordLogin(eq("user-1"), eq("org-A"), any(Instant.class));
    }
}
