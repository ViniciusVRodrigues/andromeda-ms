package andromeda.sei.ms.feature.metrics.application;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.metrics.domain.CompletedWorkItem;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryMetricsServiceTest {

    private static final String ORG = "org-1";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private VcsReadPort vcsReadPort;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private DeliveryMetricsService service;

    @Test
    void should_mapDefaultBranchAndBugLink_when_loadingMergedChanges() {
        VcsRepository repo = repo("repo-1", "trunk");
        VcsPullRequest bugFix = merged("pr-1", "repo-1", "101");
        VcsPullRequest feature = merged("pr-2", "repo-1", "102");
        when(vcsReadPort.mergedPullRequests(
                ORG, List.of("repo-1"), FROM, TO)).thenReturn(List.of(bugFix, feature));
        when(workItemRepository.findAllByOrganizationIdAndExternalIdInAndSource(
                ORG, List.of("101", "102"), WorkItem.Source.AZURE_DEVOPS))
                .thenReturn(List.of(workItem("101", WorkItem.ResolvedType.BUG),
                        workItem("102", WorkItem.ResolvedType.STORY)));

        List<MergedChange> changes = service.mergedChanges(ORG, List.of(repo), FROM, TO);

        assertThat(changes).extracting(MergedChange::id).containsExactly("pr-1", "pr-2");
        assertThat(changes).extracting(MergedChange::linkedToBug).containsExactly(true, false);
        assertThat(changes).extracting(MergedChange::defaultBranch).containsOnly("trunk");
    }

    @Test
    void should_notQuery_when_projectHasNoRepository() {
        when(vcsReadPort.repositoriesOfProject(ORG, "p1")).thenReturn(List.of());

        assertThat(service.mergedChangesOfProject(ORG, "p1", FROM, TO)).isEmpty();
        verify(vcsReadPort, never())
                .mergedPullRequests(any(), any(), any(), any());
    }

    @Test
    void should_useDefaultRules_when_organizationMissing() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());

        assertThat(service.remediationRules(ORG)).isEqualTo(RemediationRules.defaults());
    }

    @Test
    void should_keepOnlyDoneItemsWithCompletion_when_mappingBoardsData() {
        WorkItem doneBug = workItem("1", WorkItem.ResolvedType.BUG);
        doneBug.setResolvedStatus(WorkItem.ResolvedStatus.DONE);
        doneBug.setCompletedAt(FROM);
        WorkItem inProgress = workItem("2", WorkItem.ResolvedType.STORY);
        inProgress.setResolvedStatus(WorkItem.ResolvedStatus.IN_PROGRESS);
        inProgress.setCompletedAt(FROM);

        List<CompletedWorkItem> items = DeliveryMetricsService.completedItems(List.of(doneBug, inProgress));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).bug()).isTrue();
    }

    @Test
    void should_deduplicateById_when_unioningProjects() {
        MergedChange shared = new MergedChange("pr-1", "t", null, null, null, false, FROM, null, FROM);
        MergedChange other = new MergedChange("pr-2", "t", null, null, null, false, FROM, null, FROM);

        List<MergedChange> union = DeliveryMetricsService.union(List.of(List.of(shared), List.of(shared, other)),
                MergedChange::id);

        assertThat(union).extracting(MergedChange::id).containsExactly("pr-1", "pr-2");
    }

    private VcsRepository repo(String id, String defaultBranch) {
        VcsRepository repo = VcsLegacyFixtures.repository(id, ORG, "1", "octo", "hello", "octo/hello", FROM, true, FROM, FROM, 0L);
        repo.setDefaultBranch(defaultBranch);
        return repo;
    }

    private VcsPullRequest merged(String id, String repoId, String linkedExternalId) {
        VcsPullRequest pr = VcsLegacyFixtures.pullRequest(id, ORG, repoId, "ext-" + id, 1, "t", PullRequestStatus.MERGED,
                "octocat", null, FROM, FROM.plusSeconds(3600), FROM.plusSeconds(3600), FROM, null, null,
                true, FROM, 0L);
        pr.setBaseBranch("trunk");
        pr.setLinkedWorkItemExternalId(linkedExternalId);
        return pr;
    }

    private WorkItem workItem(String externalId, WorkItem.ResolvedType type) {
        WorkItem wi = WorkItemFixture.item("wi-" + externalId, type, WorkItem.ResolvedStatus.TODO);
        wi.setExternalId(externalId);
        return wi;
    }
}
