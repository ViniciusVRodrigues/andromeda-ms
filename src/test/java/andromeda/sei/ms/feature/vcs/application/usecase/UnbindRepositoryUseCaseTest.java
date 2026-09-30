package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnbindRepositoryUseCaseTest {

    @Mock private VcsRepoBindingRepository bindingRepository;

    @InjectMocks
    private UnbindRepositoryUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_softDeleteBinding_and_disableSync() {
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("binding-1", "org-1"))
                .thenReturn(Optional.of(binding("proj-1")));

        useCase.execute("proj-1", "binding-1");

        ArgumentCaptor<VcsRepoBinding> captor = ArgumentCaptor.forClass(VcsRepoBinding.class);
        verify(bindingRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().isSyncEnabled()).isFalse();
    }

    @Test
    void should_answerNotFound_when_bindingUnknown() {
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("missing", "org-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("proj-1", "missing"))
                .isInstanceOf(RepoBindingNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "REPO_BINDING_NOT_FOUND");
    }

    @Test
    void should_answerNotFound_when_bindingBelongsToAnotherProject() {
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("binding-1", "org-1"))
                .thenReturn(Optional.of(binding("proj-2")));

        assertThatThrownBy(() -> useCase.execute("proj-1", "binding-1"))
                .isInstanceOf(RepoBindingNotFoundException.class);
        verify(bindingRepository, never()).save(any());
    }

    private static VcsRepoBinding binding(String projectId) {
        return new VcsRepoBinding("binding-1", "org-1", projectId, "repo-1", VcsProvider.GITHUB, true);
    }
}
