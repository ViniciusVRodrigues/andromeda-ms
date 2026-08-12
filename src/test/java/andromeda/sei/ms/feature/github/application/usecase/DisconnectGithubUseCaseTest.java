package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisconnectGithubUseCaseTest {

    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;

    @InjectMocks
    private DisconnectGithubUseCase useCase;

    @Captor
    private ArgumentCaptor<GithubConnection> connectionCaptor;

    @Captor
    private ArgumentCaptor<List<GithubRepoBinding>> bindingsCaptor;

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
    void shouldDeactivateConnectionAndDisableBindings_keepingBindingsActive() {
        GithubConnection connection = connection();
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(List.of(binding("binding-1", "proj-1"), binding("binding-2", "proj-2")));

        useCase.execute();

        verify(githubConnectionRepository).save(connectionCaptor.capture());
        GithubConnection saved = connectionCaptor.getValue();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(GithubConnectionStatus.DISCONNECTED);

        verify(githubRepoBindingRepository).saveAll(bindingsCaptor.capture());
        List<GithubRepoBinding> savedBindings = bindingsCaptor.getValue();
        assertThat(savedBindings).hasSize(2);
        // Bindings stay active (dormant, re-enable after reconnect) but sync is disabled —
        // synced data itself is never touched.
        assertThat(savedBindings).allSatisfy(b -> {
            assertThat(b.isActive()).isTrue();
            assertThat(b.isSyncEnabled()).isFalse();
        });
    }

    @Test
    void shouldThrowConnectionNotFound_when_noActiveConnection() {
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(GithubConnectionNotFoundException.class);
    }

    private GithubConnection connection() {
        return new GithubConnection("conn-1", ORG, "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null);
    }

    private GithubRepoBinding binding(String id, String projectId) {
        return new GithubRepoBinding(id, ORG, projectId, "octo/hello", true, null, true, NOW, NOW, 1L);
    }
}
