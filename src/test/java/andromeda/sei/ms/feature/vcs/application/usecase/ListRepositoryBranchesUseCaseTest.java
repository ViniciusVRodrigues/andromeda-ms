package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.github.domain.exception.GithubPatInvalidException;
import andromeda.sei.ms.feature.vcs.application.dto.BranchListOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
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
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListRepositoryBranchesUseCaseTest {

    private static final String ORG = "org-1";
    private static final VcsCredentials CREDENTIALS = new VcsCredentials(VcsProvider.GITHUB, ORG, "pat", false);

    @Mock private VcsProviderRegistry providerRegistry;
    @Mock private VcsCredentialsProvider credentialsProvider;
    @Mock private VcsProviderClient client;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private VcsRepoBindingRepository bindingRepository;

    @InjectMocks
    private ListRepositoryBranchesUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        lenient().when(providerRegistry.credentialsProvider(VcsProvider.GITHUB)).thenReturn(credentialsProvider);
        lenient().when(credentialsProvider.credentials(ORG)).thenReturn(Optional.of(CREDENTIALS));
        lenient().when(providerRegistry.client(VcsProvider.GITHUB)).thenReturn(client);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static VcsRemoteRepository remote(String defaultBranch) {
        return new VcsRemoteRepository("123", "octo/hello", null, defaultBranch, null);
    }

    @Test
    void should_listAnUnboundRepository_byFullName() {
        when(client.resolveRepository(CREDENTIALS, "octo/hello")).thenReturn(remote("main"));
        when(client.listBranches(any(), any())).thenReturn(List.of(new VcsBranch("main", true), new VcsBranch("x", false)));

        BranchListOutput out = useCase.ofAvailable(VcsProvider.GITHUB, null, "octo/hello");

        assertThat(out.branches()).extracting(VcsBranch::name).containsExactly("main", "x");
        assertThat(out.total()).isEqualTo(2);
        assertThat(out.truncated()).isFalse();
    }

    @Test
    void should_cutAt1000_andSayTruncated() {
        when(client.getRepository(any(), any())).thenReturn(remote("b0"));
        when(client.listBranches(any(), any())).thenReturn(IntStream.rangeClosed(0, VcsProviderClient.MAX_BRANCHES_LISTED)
                .mapToObj(i -> new VcsBranch("b" + i, i == 0)).toList());

        BranchListOutput out = useCase.ofAvailable(VcsProvider.GITHUB, "123", null);

        assertThat(out.branches()).hasSize(VcsProviderClient.MAX_BRANCHES_LISTED);
        assertThat(out.total()).isEqualTo(VcsProviderClient.MAX_BRANCHES_LISTED);
        assertThat(out.truncated()).isTrue();
    }

    @Test
    void should_answer409_whenThereIsNoConnection() {
        when(credentialsProvider.credentials(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.ofAvailable(VcsProvider.GITHUB, "123", null))
                .hasFieldOrPropertyWithValue("code", "VCS_CONNECTION_NOT_FOUND");
    }

    @Test
    void should_answer404_whenTheProviderDoesNotKnowTheRepository() {
        when(client.getRepository(any(), any())).thenThrow(new RuntimeException("404"));

        assertThatThrownBy(() -> useCase.ofAvailable(VcsProvider.GITHUB, "999", null))
                .hasFieldOrPropertyWithValue("code", "REPO_NOT_FOUND");
    }

    @Test
    void should_answer502Code_whenTheProviderFailsListing() {
        when(client.getRepository(any(), any())).thenReturn(remote("main"));
        when(client.listBranches(any(), any())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> useCase.ofAvailable(VcsProvider.GITHUB, "123", null))
                .hasFieldOrPropertyWithValue("code", "VCS_LIST_BRANCHES_FAILED");
    }

    @Test
    void should_keepTheDomainErrorOfTheProvider_forAnInvalidToken() {
        when(client.getRepository(any(), any())).thenReturn(remote("main"));
        when(client.listBranches(any(), any())).thenThrow(new GithubPatInvalidException("bad token"));

        assertThatThrownBy(() -> useCase.ofAvailable(VcsProvider.GITHUB, "123", null))
                .isInstanceOf(GithubPatInvalidException.class);
    }

    @Test
    void should_listABindingsBranches() {
        VcsRepoBinding binding = new VcsRepoBinding("b-1", ORG, "p", "repo-1", VcsProvider.GITHUB, true);
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("b-1", ORG)).thenReturn(Optional.of(binding));
        when(repositoryRepository.findByIdAndOrganizationId("repo-1", ORG)).thenReturn(Optional.of(
                new VcsRepository("repo-1", ORG, VcsProvider.GITHUB, "123", "octo/hello")));
        when(client.listBranches(any(), any())).thenReturn(List.of(new VcsBranch("main", true)));

        assertThat(useCase.ofBinding("b-1").branches()).hasSize(1);
    }

    @Test
    void should_answer404_forABindingOfAnotherOrganizationOrInactive() {
        when(bindingRepository.findByIdAndOrganizationIdAndActiveTrue("b-9", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.ofBinding("b-9")).isInstanceOf(RepoBindingNotFoundException.class);
    }
}
