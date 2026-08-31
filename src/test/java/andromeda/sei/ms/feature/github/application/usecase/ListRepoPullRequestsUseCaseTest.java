package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.ProjectRepoResolver;
import andromeda.sei.ms.feature.github.application.dto.RepoPullRequestPageOutput;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestPage;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoNotFoundException;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListRepoPullRequestsUseCaseTest {

    @Mock private ProjectRepoResolver projectRepoResolver;
    @Mock private GithubPullRequestRepository githubPullRequestRepository;
    @Mock private WorkItemRepository workItemRepository;

    @InjectMocks private ListRepoPullRequestsUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectRepoOfAnotherOrganization() {
        when(projectRepoResolver.resolveOne("org-1", "repo-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("repo-x", null, null, 0, 20))
                .isInstanceOf(GithubRepoNotFoundException.class);

        verify(githubPullRequestRepository, never()).findPageByRepo(
                anyString(), anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldResolveWorkItem_when_pullRequestCarriesAKnownReference() {
        givenRepo();
        givenPage(pullRequest("pr-1", "123"));

        WorkItem workItem = workItem("wi-1", "123", "Isolar consultas por organizationId");
        when(workItemRepository.findAllByOrganizationIdAndExternalIdInAndSource(
                "org-1", List.of("123"), WorkItem.Source.AZURE_DEVOPS)).thenReturn(List.of(workItem));

        RepoPullRequestPageOutput result = useCase.execute("repo-1", null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).linkedWorkItemExternalId()).isEqualTo("123");
        assertThat(result.content().get(0).linkedWorkItemId()).isEqualTo("wi-1");
        assertThat(result.content().get(0).linkedWorkItemTitle())
                .isEqualTo("Isolar consultas por organizationId");
    }

    /**
     * Sync order across integrations is not guaranteed, so a pull request can carry a reference
     * whose work item has not been synced yet. The row must still render — unlinked, not broken.
     */
    @Test
    void shouldReturnPullRequestUnlinked_when_referencedWorkItemDoesNotExistYet() {
        givenRepo();
        givenPage(pullRequest("pr-1", "999"));

        when(workItemRepository.findAllByOrganizationIdAndExternalIdInAndSource(
                "org-1", List.of("999"), WorkItem.Source.AZURE_DEVOPS)).thenReturn(List.of());

        RepoPullRequestPageOutput result = useCase.execute("repo-1", null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).linkedWorkItemExternalId()).isNull();
        assertThat(result.content().get(0).linkedWorkItemId()).isNull();
    }

    /** The same stored reference starts resolving once the work item is finally synced. */
    @Test
    void shouldResolveLater_when_workItemIsSyncedAfterTheLink() {
        givenRepo();
        givenPage(pullRequest("pr-1", "123"));
        when(workItemRepository.findAllByOrganizationIdAndExternalIdInAndSource(
                "org-1", List.of("123"), WorkItem.Source.AZURE_DEVOPS))
                .thenReturn(List.of())
                .thenReturn(List.of(workItem("wi-1", "123", "Later")));

        assertThat(useCase.execute("repo-1", null, null, 0, 20)
                .content().get(0).linkedWorkItemId()).isNull();
        assertThat(useCase.execute("repo-1", null, null, 0, 20)
                .content().get(0).linkedWorkItemId()).isEqualTo("wi-1");
    }

    @Test
    void shouldNotQueryWorkItems_when_noPullRequestCarriesAReference() {
        givenRepo();
        givenPage(pullRequest("pr-1", null));

        useCase.execute("repo-1", null, null, 0, 20);

        verify(workItemRepository, never())
                .findAllByOrganizationIdAndExternalIdInAndSource(anyString(), anyList(), any());
    }

    private void givenRepo() {
        GithubRepo repo = new GithubRepo("repo-1", "org-1", "1", "owner", "andromeda-ms",
                "owner/andromeda-ms", null, true, Instant.now(), Instant.now(), 0L);
        when(projectRepoResolver.resolveOne("org-1", "repo-1")).thenReturn(Optional.of(repo));
    }

    private void givenPage(GithubPullRequest... prs) {
        when(githubPullRequestRepository.findPageByRepo("org-1", "repo-1", null, null, 0, 20))
                .thenReturn(new GithubPullRequestPage(List.of(prs), 0, 20, prs.length));
    }

    private GithubPullRequest pullRequest(String id, String linkedExternalId) {
        GithubPullRequest pr = new GithubPullRequest(id, "org-1", "repo-1", "100", 10,
                "Add repos tab", PullRequestStatus.MERGED, "octocat", "user-1",
                Instant.now(), null, null, null, 1.0, 0.5, true, Instant.now(), 0L);
        pr.setLinkedWorkItemExternalId(linkedExternalId);
        return pr;
    }

    private WorkItem workItem(String id, String externalId, String title) {
        WorkItem item = new WorkItem(id, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, null, title, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, true, null, Instant.now(),
                null, null, null, null, null, null, null, null);
        return item;
    }
}
