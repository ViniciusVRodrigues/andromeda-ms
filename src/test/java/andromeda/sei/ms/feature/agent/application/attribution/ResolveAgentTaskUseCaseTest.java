package andromeda.sei.ms.feature.agent.application.attribution;

import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.organization.domain.AgentSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveAgentTaskUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final String P1 = "proj-1";
    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    @Mock private WorkItemRepository workItems;
    @Mock private SprintRepository sprints;
    @Mock private OrganizationRepository organizations;
    @Mock private AllocationTypeEligibility eligibility;
    @Mock private EffectivePermissionService permissions;
    @Mock private RemoteRepositoryResolver remote;

    private ResolveAgentTaskUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ResolveAgentTaskUseCase(workItems, sprints, organizations, eligibility, permissions, remote);
        member(Set.of(P1));
        lenient().when(eligibility.isEligible(eq(ORG), any())).thenReturn(true);
        lenient().when(remote.resolveProjectId(anyString(), any())).thenReturn(Optional.empty());
    }

    private void member(Set<String> projectIds) {
        lenient().when(permissions.snapshot(ORG, USER)).thenReturn(Optional.of(
                new UserPermissionSnapshot(ORG, USER, "r", "DEVELOPER", List.of(), List.of(), projectIds, 1L)));
    }

    private WorkItem item(String id, String ext, String project, WorkItem.Source source, String parent, String type) {
        return new WorkItem(id, ORG, project, source, ext, T0, "Title " + ext, type, WorkItem.ResolvedType.STORY,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS, "2", List.of(), "sp-1", null, USER, null, parent,
                T0, null, null, null, null, true, 1L, T0, null, null, null, null, null, null, null, null);
    }

    private WorkItem azure(String ext) {
        return item("wi-" + ext, ext, P1, WorkItem.Source.AZURE_DEVOPS, null, "Story");
    }

    private AgentTaskQuery query(String taskRef, String branch, Instant bucket, ActiveTaskHint hint) {
        return new AgentTaskQuery(ORG, USER, taskRef, branch, "github.com/o/r", bucket, hint);
    }

    private void exists(WorkItem... items) {
        for (WorkItem i : items) {
            lenient().when(workItems.findAllActiveByOrganizationIdAndExternalId(ORG, i.getExternalId()))
                    .thenReturn(List.of(i));
            lenient().when(workItems.findByOrganizationIdAndExternalIdAndSource(ORG, i.getExternalId(), i.getSource()))
                    .thenReturn(Optional.of(i));
        }
    }

    @Test
    void should_preferTaskRefOverBranch() {
        exists(azure("123"), azure("999"));

        var r = useCase.execute(query("AB#123", "feature/AB#999-x", T0, null));

        assertThat(r.source()).isEqualTo(AttributionSource.TASK_REF);
        assertThat(r.resolvedTask().workItemId()).isEqualTo("wi-123");
        assertThat(r.resolvedTask().ref()).isEqualTo("AB#123");
        assertThat(r.resolvedTask().projectId()).isEqualTo(P1);
    }

    @Test
    void should_useJiraKeyAsRef() {
        exists(item("wi-j", "PROJ-45", P1, WorkItem.Source.JIRA, null, "Story"));
        assertThat(useCase.resolveRef(ORG, USER, "proj-45").orElseThrow().ref()).isEqualTo("PROJ-45");
    }

    @Test
    void should_useActiveTask_onlyWhenBucketAfterSinceAndRepositoryMatches() {
        exists(azure("123"));
        Instant since = T0;
        var hint = new ActiveTaskHint("AB#123", since, "https://github.com/O/R.git");

        assertThat(useCase.execute(query(null, null, T0, hint)).source()).isEqualTo(AttributionSource.ACTIVE_TASK);
        assertThat(useCase.execute(query(null, null, T0.minusSeconds(1), hint)).source()).isEqualTo(AttributionSource.NONE);
        var otherRepo = new ActiveTaskHint("AB#123", since, "github.com/x/y");
        assertThat(useCase.execute(query(null, null, T0, otherRepo)).source()).isEqualTo(AttributionSource.NONE);
        var noRepo = new ActiveTaskHint("AB#123", since, null);
        assertThat(useCase.execute(query(null, null, T0, noRepo)).source()).isEqualTo(AttributionSource.ACTIVE_TASK);
    }

    @Test
    void should_resolveByBranch_withDefaultAndCustomPatterns() {
        exists(azure("123"), azure("77"));

        assertThat(useCase.execute(query(null, "feature/AB#123-login", T0, null)).source())
                .isEqualTo(AttributionSource.BRANCH);

        Organization org = mock(Organization.class);
        when(org.getAgentSettings()).thenReturn(new AgentSettings(List.of("^wi-(\\d+)")));
        when(organizations.findById(ORG)).thenReturn(Optional.of(org));
        var r = useCase.execute(query(null, "wi-77", T0, null));
        assertThat(r.resolvedTask().workItemId()).isEqualTo("wi-77");
    }

    @Test
    void should_notResolve_when_userCannotAccessProject() {
        exists(item("wi-5", "5", "proj-other", WorkItem.Source.AZURE_DEVOPS, null, "Story"));
        assertThat(useCase.execute(query("5", null, T0, null)).source()).isEqualTo(AttributionSource.NONE);
    }

    @Test
    void should_climbToEligibleParent_when_taskIsNotEligible() {
        WorkItem task = item("wi-t", "10", P1, WorkItem.Source.AZURE_DEVOPS, "20", "Task");
        WorkItem story = item("wi-s", "20", P1, WorkItem.Source.AZURE_DEVOPS, null, "Story");
        exists(task, story);
        when(eligibility.isEligible(ORG, task)).thenReturn(false);

        var r = useCase.execute(query("10", null, T0, null));

        assertThat(r.resolvedTask().workItemId()).isEqualTo("wi-s");
    }

    @Test
    void should_notResolve_when_noEligibleAncestorWithinThreeLevels() {
        WorkItem a = item("a", "1", P1, WorkItem.Source.AZURE_DEVOPS, "2", "Task");
        WorkItem b = item("b", "2", P1, WorkItem.Source.AZURE_DEVOPS, "3", "Task");
        WorkItem c = item("c", "3", P1, WorkItem.Source.AZURE_DEVOPS, "4", "Task");
        WorkItem d = item("d", "4", P1, WorkItem.Source.AZURE_DEVOPS, "5", "Task");
        WorkItem e = item("e", "5", P1, WorkItem.Source.AZURE_DEVOPS, null, "Story");
        exists(a, b, c, d, e);
        when(eligibility.isEligible(eq(ORG), any())).thenAnswer(i -> "Story".equals(((WorkItem) i.getArgument(1)).getRawType()));

        assertThat(useCase.execute(query("1", null, T0, null)).source()).isEqualTo(AttributionSource.NONE);
        assertThat(useCase.execute(query("2", null, T0, null)).resolvedTask().workItemId()).isEqualTo("e");
    }

    @Test
    void should_notResolve_when_sameExternalIdInTwoSources() {
        when(workItems.findAllActiveByOrganizationIdAndExternalId(ORG, "123")).thenReturn(List.of(
                azure("123"), item("wi-j", "123", P1, WorkItem.Source.JIRA, null, "Story")));
        assertThat(useCase.execute(query("123", null, T0, null)).source()).isEqualTo(AttributionSource.NONE);
    }

    @Test
    void should_resolveSingleActive_onlyWhenExactlyOneInProgress() {
        when(remote.resolveProjectId(ORG, "github.com/o/r")).thenReturn(Optional.of(P1));
        Sprint sprint = mock(Sprint.class);
        when(sprint.getId()).thenReturn("sp-1");
        when(sprints.findCurrentSprintByOrganizationIdAndProjectId(ORG, P1)).thenReturn(Optional.of(sprint));
        when(workItems.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sp-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(azure("1")))
                .thenReturn(List.of(azure("1"), azure("2")));

        var one = useCase.execute(query(null, "main", T0, null));
        assertThat(one.source()).isEqualTo(AttributionSource.SINGLE_ACTIVE);
        assertThat(one.resolvedTask().workItemId()).isEqualTo("wi-1");
        assertThat(one.repositoryProjectId()).isEqualTo(P1);

        var two = useCase.execute(query(null, "main", T0, null));
        assertThat(two.source()).isEqualTo(AttributionSource.NONE);
        assertThat(two.resolvedTask()).isNull();
        assertThat(two.repositoryProjectId()).isEqualTo(P1);
    }
}
