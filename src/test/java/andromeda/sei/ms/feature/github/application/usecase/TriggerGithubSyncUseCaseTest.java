package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriggerGithubSyncUseCaseTest {

    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;
    @Mock private SyncGithubRepoDataUseCase syncGithubRepoDataUseCase;

    @InjectMocks
    private TriggerGithubSyncUseCase useCase;

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSyncEverySyncEnabledActiveBinding_withManualTrigger() {
        GithubConnection connection = connection();
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(ORG))
                .thenReturn(List.of(binding("binding-1"), binding("binding-2")));

        useCase.execute();

        verify(syncGithubRepoDataUseCase, times(2))
                .execute(eq(connection), any(GithubRepoBinding.class), eq(SyncTrigger.MANUAL));
    }

    @Test
    void shouldThrowConnectionNotFound_when_noActiveConnection() {
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(GithubConnectionNotFoundException.class);
    }

    @Test
    void shouldIsolatePerBindingFailures_andContinueWithRemainingBindings() {
        GithubConnection connection = connection();
        GithubRepoBinding failing = binding("binding-fail");
        GithubRepoBinding ok = binding("binding-ok");
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(ORG))
                .thenReturn(List.of(failing, ok));
        doThrow(new RuntimeException("GitHub API blew up"))
                .when(syncGithubRepoDataUseCase).execute(connection, failing, SyncTrigger.MANUAL);

        assertThatCode(() -> useCase.execute()).doesNotThrowAnyException();

        verify(syncGithubRepoDataUseCase).execute(connection, ok, SyncTrigger.MANUAL);
    }

    @Test
    void shouldNotTouchCircuitBreakerFields_onTheConnection() {
        GithubConnection connection = connection();
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(ORG))
                .thenReturn(List.of());

        useCase.execute();

        // The circuit-breaker state is owned by the scheduled job (design.md D7).
        assertThat(connection.getConsecutiveFailures()).isZero();
        assertThat(connection.getLastSuccessAt()).isNull();
        verify(githubConnectionRepository, times(0)).save(any());
    }

    private GithubConnection connection() {
        return new GithubConnection("conn-1", ORG, "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null);
    }

    private GithubRepoBinding binding(String id) {
        return new GithubRepoBinding(id, ORG, "proj-" + id, "octo/" + id, true, null, true, NOW, NOW, 1L);
    }
}
