package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.BindGithubRepoInput;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoAlreadyBoundException;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoIdInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class BindGithubRepoUseCaseTest {

    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;

    @InjectMocks
    private BindGithubRepoUseCase useCase;

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
    void shouldThrowAlreadyBound_when_repoActivelyBoundToDifferentProject() {
        when(githubRepoBindingRepository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG, "octo/hello"))
                .thenReturn(Optional.of(binding("binding-1", "proj-OTHER", "octo/hello")));

        assertThatThrownBy(() -> useCase.execute("proj-1", new BindGithubRepoInput("octo/hello", true)))
                .isInstanceOf(GithubRepoAlreadyBoundException.class)
                .satisfies(ex -> assertThat(((GithubRepoAlreadyBoundException) ex).getCode())
                        .isEqualTo("GITHUB_REPO_ALREADY_BOUND"));

        verify(githubRepoBindingRepository, never()).save(any());
    }

    @Test
    void shouldUpsertUnchanged_when_rebindingSameRepoToSameProject() {
        GithubRepoBinding existing = binding("binding-1", "proj-1", "octo/hello");
        when(githubRepoBindingRepository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG, "octo/hello"))
                .thenReturn(Optional.of(existing));
        when(githubRepoBindingRepository
                .findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(ORG, "proj-1", "octo/hello"))
                .thenReturn(Optional.of(existing));
        when(githubRepoBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GithubRepoBinding result = useCase.execute("proj-1", new BindGithubRepoInput("octo/hello", false));

        assertThat(result.getId()).isEqualTo("binding-1");
        assertThat(result.isSyncEnabled()).isFalse();
        verify(githubRepoBindingRepository).save(existing);
    }

    @Test
    void shouldCreateBinding_when_noActiveBindingExistsForRepo() {
        when(githubRepoBindingRepository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG, "octo/hello"))
                .thenReturn(Optional.empty());
        when(githubRepoBindingRepository
                .findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(ORG, "proj-1", "octo/hello"))
                .thenReturn(Optional.empty());
        when(githubRepoBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GithubRepoBinding result = useCase.execute("proj-1", new BindGithubRepoInput("octo/hello", true));

        assertThat(result.getProjectId()).isEqualTo("proj-1");
        assertThat(result.getRepoId()).isEqualTo("octo/hello");
        assertThat(result.isSyncEnabled()).isTrue();
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void shouldNotBlock_when_previousBindingIsInactive() {
        // The port method filters active=true, so an inactive binding simply never surfaces —
        // the repo is free to bind anywhere again.
        when(githubRepoBindingRepository.findByOrganizationIdAndRepoIdAndActiveTrue(ORG, "octo/hello"))
                .thenReturn(Optional.empty());
        when(githubRepoBindingRepository
                .findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(ORG, "proj-2", "octo/hello"))
                .thenReturn(Optional.empty());
        when(githubRepoBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GithubRepoBinding result = useCase.execute("proj-2", new BindGithubRepoInput("octo/hello", true));

        assertThat(result.getProjectId()).isEqualTo("proj-2");
    }

    private GithubRepoBinding binding(String id, String projectId, String repoId) {
        return new GithubRepoBinding(id, ORG, projectId, repoId, true, null, true, NOW, NOW, 1L);
    }

    /**
     * O repoId do binding E a chave owner/name pela qual o sync busca o repo, entao um valor
     * malformado produz um binding que nunca sincroniza. Rejeitar no bind mantem a falha junto
     * da entrada que a causou, em vez de estourar na primeira execucao do sync.
     */
    @Test
    void shouldRejectBinding_when_repoIdIsNotOwnerSlashName() {
        for (String malformed : new String[] { "andromeda-ms", "/andromeda-ms", "owner/", "a/b/c" }) {
            assertThatThrownBy(() -> useCase.execute("proj-1", new BindGithubRepoInput(malformed, true)))
                    .isInstanceOf(GithubRepoIdInvalidException.class);
        }
        verify(githubRepoBindingRepository, never()).save(any());
    }
}
