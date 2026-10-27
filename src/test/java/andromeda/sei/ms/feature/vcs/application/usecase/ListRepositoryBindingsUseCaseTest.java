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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListRepositoryBindingsUseCaseTest {

    @Mock private VcsRepoBindingRepository bindingRepository;
    @Mock private VcsRepositoryRepository repositoryRepository;
    @Mock private andromeda.sei.ms.feature.organization.domain.OrganizationRepository organizationRepository;

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
    void should_returnEffectiveBranchFields_andTheBackfillWindow() {
        VcsRepoBinding legacy = new VcsRepoBinding("b-1", "org-1", "proj-1", "repo-1", VcsProvider.GITHUB, true);
        VcsRepoBinding chosen = new VcsRepoBinding("b-2", "org-1", "proj-1", "repo-2", VcsProvider.GITHUB, true);
        chosen.setBranches(List.of("develop", "main"));
        chosen.setProductionBranch("main");
        chosen.setFilterPullRequestsByBranch(true);
        chosen.setMissingBranches(List.of("old"));
        chosen.setSyncState(andromeda.sei.ms.feature.vcs.domain.VcsSyncState.FAILED);
        chosen.setSyncStateAt(java.time.Instant.now());
        when(bindingRepository.findAllActiveByOrganizationIdAndProjectId("org-1", "proj-1"))
                .thenReturn(List.of(legacy, chosen));
        VcsRepository r1 = new VcsRepository("repo-1", "org-1", VcsProvider.GITHUB, "1", "octo/a");
        r1.setDefaultBranch("trunk");
        VcsRepository r2 = new VcsRepository("repo-2", "org-1", VcsProvider.GITHUB, "2", "octo/b");
        r2.setDefaultBranch("develop");
        when(repositoryRepository.findAllByOrganizationIdAndIdIn(any(), any())).thenReturn(List.of(r1, r2));
        andromeda.sei.ms.feature.organization.domain.Organization orgDoc =
                org.mockito.Mockito.mock(andromeda.sei.ms.feature.organization.domain.Organization.class);
        when(orgDoc.getSyncSettings()).thenReturn(andromeda.sei.ms.feature.organization.domain.SyncSettings.defaults());
        when(organizationRepository.findById("org-1")).thenReturn(java.util.Optional.of(orgDoc));

        List<RepoBindingOutput> out = useCase.execute("proj-1");

        RepoBindingOutput first = out.get(0);
        assertThat(first.defaultBranch()).isEqualTo("trunk");
        assertThat(first.branches()).containsExactly("trunk");
        assertThat(first.productionBranch()).isEqualTo("trunk");
        assertThat(first.branchesConfigured()).isFalse();
        assertThat(first.filterPullRequestsByBranch()).isFalse();
        assertThat(first.maxBranches()).isEqualTo(10);
        assertThat(first.backfillDays()).isEqualTo(90);
        assertThat(first.resynced()).isFalse();
        RepoBindingOutput second = out.get(1);
        assertThat(second.branches()).containsExactly("develop", "main");
        assertThat(second.productionBranch()).isEqualTo("main");
        assertThat(second.branchesConfigured()).isTrue();
        assertThat(second.filterPullRequestsByBranch()).isTrue();
        assertThat(second.missingBranches()).containsExactly("old");
        assertThat(second.syncState()).isEqualTo(andromeda.sei.ms.feature.vcs.domain.VcsSyncState.FAILED);
    }

    @Test
    void should_listEveryOrganizationBinding_whenNoProject() {
        when(bindingRepository.findAllActiveByOrganizationId("org-1")).thenReturn(List.of());

        assertThat(useCase.execute(null)).isEmpty();
        verify(bindingRepository).findAllActiveByOrganizationId("org-1");
    }
}
