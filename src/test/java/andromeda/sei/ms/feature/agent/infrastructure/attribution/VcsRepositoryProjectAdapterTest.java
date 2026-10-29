package andromeda.sei.ms.feature.agent.infrastructure.attribution;

import andromeda.sei.ms.feature.agent.application.attribution.RemoteRepositoryResolver;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class VcsRepositoryProjectAdapterTest {

    private static final String ORG = "org-1";

    @Mock private VcsRepositoryRepository repositories;
    @Mock private VcsRepoBindingRepository bindings;

    private RemoteRepositoryResolver resolver() {
        return new RemoteRepositoryResolver(new VcsRepositoryProjectAdapter(repositories, bindings));
    }

    private VcsRepository repo(String id, VcsProvider p, String name) {
        return new VcsRepository(id, ORG, p, "ext-" + id, name);
    }

    private VcsRepoBinding bind(String repoId, String projectId) {
        return new VcsRepoBinding("b-" + repoId + projectId, ORG, projectId, repoId, VcsProvider.GITHUB, true);
    }

    private void given(VcsProvider p, List<VcsRepository> repos, List<VcsRepoBinding> bs) {
        lenient().when(repositories.findAllByOrganizationIdAndProvider(ORG, p)).thenReturn(repos);
        lenient().when(bindings.findAllActiveByOrganizationId(ORG)).thenReturn(bs);
    }

    @Test
    void should_resolveGithubProject_ignoringCase() {
        given(VcsProvider.GITHUB, List.of(repo("r1", VcsProvider.GITHUB, "Owner/Repo")), List.of(bind("r1", "p1")));
        assertThat(resolver().resolveProjectId(ORG, "https://github.com/owner/REPO.git")).contains("p1");
    }

    @Test
    void should_resolveAzureProject_byUniqueName() {
        given(VcsProvider.AZURE_REPOS, List.of(repo("r2", VcsProvider.AZURE_REPOS, "Repo")), List.of(bind("r2", "p2")));
        assertThat(resolver().resolveProjectId(ORG, "https://org.visualstudio.com/proj/_git/repo")).contains("p2");
    }

    @Test
    void should_notResolve_when_azureNameIsAmbiguous() {
        given(VcsProvider.AZURE_REPOS, List.of(repo("r2", VcsProvider.AZURE_REPOS, "Repo"),
                repo("r3", VcsProvider.AZURE_REPOS, "repo")), List.of(bind("r2", "p2"), bind("r3", "p3")));
        assertThat(resolver().resolveProjectId(ORG, "dev.azure.com/org/proj/repo")).isEmpty();
    }

    @Test
    void should_notResolve_when_noBinding() {
        given(VcsProvider.GITHUB, List.of(repo("r1", VcsProvider.GITHUB, "o/r")), List.of());
        assertThat(resolver().resolveProjectId(ORG, "github.com/o/r")).isEmpty();
    }

    @Test
    void should_notResolve_when_boundToTwoProjects() {
        given(VcsProvider.GITHUB, List.of(repo("r1", VcsProvider.GITHUB, "o/r")), List.of(bind("r1", "p1"), bind("r1", "p2")));
        assertThat(resolver().resolveProjectId(ORG, "github.com/o/r")).isEmpty();
    }

    @Test
    void should_notResolve_when_remoteUnknown() {
        assertThat(resolver().resolveProjectId(ORG, "gitlab.com/o/r")).isEmpty();
        assertThat(resolver().resolveProjectId(ORG, null)).isEmpty();
    }
}
