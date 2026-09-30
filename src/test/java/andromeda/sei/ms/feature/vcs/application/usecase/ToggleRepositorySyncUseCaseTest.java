package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleRepositorySyncUseCaseTest {

    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private VcsRepositoryRepository repositoryRepository;

    @InjectMocks
    private ToggleRepositorySyncUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_disableSync_keepingTheBindingActive() {
        VcsRepoBinding binding = new VcsRepoBinding("b-1", "org-1", "proj-1", "repo-1", VcsProvider.GITHUB, true);
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("b-1", "org-1")).thenReturn(Optional.of(binding));
        when(bindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repositoryRepository.findByIdAndOrganizationId("repo-1", "org-1")).thenReturn(Optional.of(
                new VcsRepository("repo-1", "org-1", VcsProvider.GITHUB, "123", "octo/hello")));

        RepoBindingOutput result = useCase.execute("b-1", false);

        assertThat(result.syncEnabled()).isFalse();
        assertThat(result.displayName()).isEqualTo("octo/hello");
        assertThat(binding.isActive()).isTrue();
    }

    @Test
    void should_answerNotFound_when_bindingOfAnotherOrganization() {
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("b-x", "org-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("b-x", true)).isInstanceOf(RepoBindingNotFoundException.class);
    }
}
