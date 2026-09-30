package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListRepositoryBindingsUseCaseTest {

    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private VcsRepositoryRepository repositoryRepository;

    @InjectMocks
    private ListRepositoryBindingsUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_listProjectBindings_withDisplayNamesInOneBatch() {
        when(bindingRepository.findAllActiveByOrganizationIdAndProjectId("org-1", "proj-1")).thenReturn(List.of(
                new VcsRepoBinding("b-1", "org-1", "proj-1", "repo-1", VcsProvider.GITHUB, true),
                new VcsRepoBinding("b-2", "org-1", "proj-1", "repo-gone", VcsProvider.GITHUB, false)));
        when(repositoryRepository.findAllByOrganizationIdAndIdIn("org-1", List.of("repo-1", "repo-gone"))).thenReturn(List.of(
                new VcsRepository("repo-1", "org-1", VcsProvider.GITHUB, "123", "octo/hello")));

        List<RepoBindingOutput> bindings = useCase.execute("proj-1");

        assertThat(bindings).extracting(RepoBindingOutput::displayName).containsExactly("octo/hello", null);
        assertThat(bindings).extracting(RepoBindingOutput::provider).containsOnly(VcsProvider.GITHUB);
    }

    @Test
    void should_listEveryOrganizationBinding_whenNoProject() {
        when(bindingRepository.findAllActiveByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute(null)).isEmpty();
        verify(bindingRepository).findAllActiveByOrganizationId("org-1");
    }
}
