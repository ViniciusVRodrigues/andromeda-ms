package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.BindRepositoryInput;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoAlreadyBoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoIdInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsProviderUnsupportedException;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** vcs-core 5.1 / github-integration "Repo binding" and "exclusivity" requirements. */
@ExtendWith(MockitoExtension.class)
class BindRepositoryUseCaseTest {

    private static final String ORG = "org-1";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsCredentialsProvider credentialsProvider;
    @Mock private VcsProviderClient client;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private VcsRepoBindingRepository bindingRepository;

    @InjectMocks
    private BindRepositoryUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        lenient().when(providerRegistry.credentialsProvider(VcsProvider.GITHUB)).thenReturn(credentialsProvider);
        lenient().when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(CREDENTIALS));
        lenient().when(providerRegistry.client(VcsProvider.GITHUB)).thenReturn(client);
        lenient().when(repositoryRepository.save(any())).thenAnswer(inv -> {
            VcsRepository repo = inv.getArgument(0);
            if (repo.getId() == null) repo.setId("repo-new");
            return repo;
        });
        lenient().when(bindingRepository.save(any())).thenAnswer(inv -> {
            VcsRepoBinding binding = inv.getArgument(0);
            if (binding.getId() == null) binding.setId("binding-new");
            return binding;
        });
        lenient().when(repositoryRepository.findByOrganizationIdAndProviderAndExternalRepoId(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(repositoryRepository.findByOrganizationIdAndProviderAndDisplayName(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_resolveOwnerSlashName_toNumericId_andBindInternalRepository() {
        when(client.resolveRepository(CREDENTIALS, "octo/hello")).thenReturn(remote());
        when(bindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-new")).thenReturn(Optional.empty());

        RepoBindingOutput result = useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, null, "octo/hello", true));

        assertThat(result.id()).isEqualTo("binding-new");
        assertThat(result.repositoryId()).isEqualTo("repo-new");
        assertThat(result.provider()).isEqualTo(VcsProvider.GITHUB);
        assertThat(result.displayName()).isEqualTo("octo/hello");
        assertThat(result.syncEnabled()).isTrue();
    }

    @Test
    void should_reuseKnownRepository_when_boundByExternalId() {
        VcsRepository known = new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello");
        when(repositoryRepository.findByOrganizationIdAndProviderAndExternalRepoId(ORG, VcsProvider.GITHUB, "123"))
                .thenReturn(Optional.of(known));
        when(bindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-1")).thenReturn(Optional.empty());

        RepoBindingOutput result = useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, "123", null, false));

        assertThat(result.repositoryId()).isEqualTo("repo-1");
        assertThat(result.syncEnabled()).isFalse();
        verifyNoInteractions(client);
    }

    @Test
    void should_throwAlreadyBound_when_repositoryActivelyBoundToDifferentProject() {
        givenKnownRepository();
        when(bindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-1"))
                .thenReturn(Optional.of(binding("binding-1", "proj-2", true)));

        assertThatThrownBy(() -> useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, "123", null, true)))
                .isInstanceOf(RepoAlreadyBoundException.class)
                .hasFieldOrPropertyWithValue("code", "REPO_ALREADY_BOUND");
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void should_updateSyncFlagOnly_when_rebindingToSameProject() {
        givenKnownRepository();
        when(bindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-1"))
                .thenReturn(Optional.of(binding("binding-1", "proj-1", true)));

        RepoBindingOutput result = useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, "123", null, false));

        assertThat(result.id()).isEqualTo("binding-1");
        assertThat(result.syncEnabled()).isFalse();
    }

    @Test
    void should_adoptPendingRepository_when_bindingTheSameName() {
        VcsRepository pending = new VcsRepository("repo-pending", ORG, VcsProvider.GITHUB, null, "octo/hello");
        when(repositoryRepository.findByOrganizationIdAndProviderAndDisplayName(ORG, VcsProvider.GITHUB, "octo/hello"))
                .thenReturn(Optional.of(pending));
        when(client.resolveRepository(CREDENTIALS, "octo/hello")).thenReturn(remote());
        when(bindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-pending")).thenReturn(Optional.empty());

        RepoBindingOutput result = useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, null, "octo/hello", true));

        assertThat(result.repositoryId()).isEqualTo("repo-pending");
        assertThat(pending.getExternalRepoId()).isEqualTo("123");
    }

    @Test
    void should_requireAnActiveConnection() {
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, "123", null, true)))
                .isInstanceOf(VcsConnectionNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "VCS_CONNECTION_NOT_FOUND");
        verifyNoInteractions(repositoryRepository, bindingRepository);
    }

    @Test
    void should_rejectUnsupportedProvider() {
        when(providerRegistry.credentialsProvider(VcsProvider.AZURE_REPOS))
                .thenThrow(new VcsProviderUnsupportedException("AZURE_REPOS"));

        assertThatThrownBy(() -> useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.AZURE_REPOS, "g", null, true)))
                .hasFieldOrPropertyWithValue("code", "VCS_PROVIDER_UNSUPPORTED");
    }

    @Test
    void should_rejectMissingIdentifier_withRepoIdInvalid() {
        assertThatThrownBy(() -> useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, " ", null, true)))
                .isInstanceOf(RepoIdInvalidException.class);
    }

    @Test
    void should_propagateRepoIdInvalid_fromTheProvider() {
        when(client.resolveRepository(CREDENTIALS, "no-slash")).thenThrow(new RepoIdInvalidException("no-slash"));

        assertThatThrownBy(() -> useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, null, "no-slash", true)))
                .hasFieldOrPropertyWithValue("code", "REPO_ID_INVALID");
    }

    @Test
    void should_answerRepoNotFound_when_providerDoesNotKnowTheRepository() {
        when(client.resolveRepository(CREDENTIALS, "octo/ghost")).thenThrow(new RuntimeException("404 Not Found"));

        assertThatThrownBy(() -> useCase.execute("proj-1", new BindRepositoryInput(VcsProvider.GITHUB, null, "octo/ghost", true)))
                .isInstanceOf(RepoNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "REPO_NOT_FOUND");
    }

    @Test
    void should_defaultToGithub_when_providerOmitted() {
        givenKnownRepository();
        when(bindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "repo-1")).thenReturn(Optional.empty());

        assertThat(useCase.execute("proj-1", new BindRepositoryInput(null, "123", null, true)).provider())
                .isEqualTo(VcsProvider.GITHUB);
    }

    private void givenKnownRepository() {
        when(repositoryRepository.findByOrganizationIdAndProviderAndExternalRepoId(ORG, VcsProvider.GITHUB, "123"))
                .thenReturn(Optional.of(new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello")));
    }

    private static VcsRemoteRepository remote() {
        return new VcsRemoteRepository("123", "octo/hello", null, "main", null);
    }

    private static VcsRepoBinding binding(String id, String projectId, boolean syncEnabled) {
        return new VcsRepoBinding(id, ORG, projectId, "repo-1", VcsProvider.GITHUB, syncEnabled);
    }
}
