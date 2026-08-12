package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnbindGithubRepoUseCaseTest {

    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;

    @InjectMocks
    private UnbindGithubRepoUseCase useCase;

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
    void shouldSoftDeleteBinding_onSuccess() {
        GithubRepoBinding binding = binding("binding-1", "proj-1");
        when(githubRepoBindingRepository.findByIdAndOrganizationIdAndActiveTrue("binding-1", ORG))
                .thenReturn(Optional.of(binding));
        when(githubRepoBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute("proj-1", "binding-1");

        ArgumentCaptor<GithubRepoBinding> captor = ArgumentCaptor.forClass(GithubRepoBinding.class);
        verify(githubRepoBindingRepository).save(captor.capture());
        // Soft delete — synced data is kept; the freed repoId becomes bindable elsewhere.
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().isSyncEnabled()).isFalse();
    }

    @Test
    void shouldThrowNotFound_when_bindingIdUnknown() {
        when(githubRepoBindingRepository.findByIdAndOrganizationIdAndActiveTrue("missing", ORG))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("proj-1", "missing"))
                .isInstanceOf(GithubRepoBindingNotFoundException.class)
                .satisfies(ex -> assertThat(((GithubRepoBindingNotFoundException) ex).getCode())
                        .isEqualTo("GITHUB_REPO_BINDING_NOT_FOUND"));

        verify(githubRepoBindingRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFound_when_bindingBelongsToDifferentProject() {
        // An id that belongs to another project is "not found" within this project's scope
        // (same 404 code, not 403/400 — design.md D8).
        GithubRepoBinding binding = binding("binding-1", "proj-OTHER");
        when(githubRepoBindingRepository.findByIdAndOrganizationIdAndActiveTrue("binding-1", ORG))
                .thenReturn(Optional.of(binding));

        assertThatThrownBy(() -> useCase.execute("proj-1", "binding-1"))
                .isInstanceOf(GithubRepoBindingNotFoundException.class)
                .satisfies(ex -> assertThat(((GithubRepoBindingNotFoundException) ex).getCode())
                        .isEqualTo("GITHUB_REPO_BINDING_NOT_FOUND"));

        verify(githubRepoBindingRepository, never()).save(any());
    }

    private GithubRepoBinding binding(String id, String projectId) {
        return new GithubRepoBinding(id, ORG, projectId, "octo/hello", true, null, true, NOW, NOW, 1L);
    }
}
