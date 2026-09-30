package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * vcs-core "Provider-scoped data wipe" (task 6.6). Inspects the predicates actually handed to Mongo:
 * a missing tenant or provider filter in an irreversible operation is the dangerous class of bug.
 * Mocked {@link MongoTemplate} (no embedded Mongo under Boot 4 here).
 */
@ExtendWith(MockitoExtension.class)
class VcsIntegrationDataWiperTest {

    private static final String ORG = "org-1";

    @Mock private MongoTemplate mongoTemplate;

    private VcsIntegrationDataWiper wiper;

    @BeforeEach
    void setUp() {
        wiper = new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.GITHUB);
        lenient().when(mongoTemplate.remove(any(Query.class), anyString())).thenReturn(DeleteResult.acknowledged(2));
        lenient().when(mongoTemplate.find(any(Query.class), eq(VcsRepositoryDocument.class))).thenReturn(List.of());
        lenient().when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(VcsRepoBindingDocument.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    @Test
    void should_wipeTheGithubIntegration() {
        assertThat(wiper.integration()).isEqualTo(Integration.GITHUB);
    }

    @Test
    void should_filterEveryRemovalByOrganizationAndProvider() {
        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, atLeastOnce()).remove(captor.capture(), anyString());
        assertThat(captor.getAllValues()).hasSize(4).allSatisfy(query -> {
            assertThat(query.getQueryObject().get("organizationId")).isEqualTo(ORG);
            assertThat(query.getQueryObject().get("provider")).isEqualTo(VcsProvider.GITHUB);
        });
    }

    @Test
    void should_wipeExactlyTheProjectRepositories_resolvedThroughActiveBindings() {
        when(mongoTemplate.find(any(Query.class), eq(VcsRepoBindingDocument.class))).thenReturn(List.of(
                binding("repo-a"), binding("repo-b")));

        Map<String, Long> removed = wiper.wipe(ORG, WipeScope.forProject("proj-a"));

        ArgumentCaptor<Query> bindingQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, atLeastOnce()).find(bindingQuery.capture(), eq(VcsRepoBindingDocument.class));
        Document bindingFilter = bindingQuery.getAllValues().getFirst().getQueryObject();
        assertThat(bindingFilter.get("projectId")).isEqualTo("proj-a");
        assertThat(bindingFilter.get("active")).isEqualTo(true);

        ArgumentCaptor<Query> removal = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<String> collection = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(removal.capture(), collection.capture());
        assertThat(collection.getAllValues()).containsExactly("vcs_review_events", "vcs_pull_requests",
                "vcs_commits", "vcs_repositories");
        for (int i = 0; i < 3; i++) {
            assertThat(inValues(removal.getAllValues().get(i), "repositoryId")).containsExactly("repo-a", "repo-b");
        }
        assertThat(inValues(removal.getAllValues().get(3), "_id")).containsExactly("repo-a", "repo-b");
        assertThat(removed).containsEntry("vcs_pull_requests", 2L);
    }

    @Test
    void should_doNothing_when_projectHasNoActiveBinding() {
        when(mongoTemplate.find(any(Query.class), eq(VcsRepoBindingDocument.class))).thenReturn(List.of());

        assertThat(wiper.wipe(ORG, WipeScope.forProject("proj-empty"))).isEmpty();
        verify(mongoTemplate, never()).remove(any(Query.class), anyString());
    }

    @Test
    void should_recreateBoundRepositoriesAsPendingStubs_andClearWatermarks() {
        VcsRepositoryDocument wiped = VcsRepositoryDocument.builder().id("repo-a").organizationId(ORG)
                .provider(VcsProvider.GITHUB).externalRepoId("123").displayName("octo/a").defaultBranch("main").build();
        when(mongoTemplate.find(any(Query.class), eq(VcsRepositoryDocument.class))).thenReturn(List.of(wiped));
        when(mongoTemplate.find(any(Query.class), eq(VcsRepoBindingDocument.class))).thenReturn(List.of(binding("repo-a")));

        wiper.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<VcsRepositoryDocument> stub = ArgumentCaptor.forClass(VcsRepositoryDocument.class);
        verify(mongoTemplate).insert(stub.capture());
        assertThat(stub.getValue().getId()).isEqualTo("repo-a");
        assertThat(stub.getValue().getDisplayName()).isEqualTo("octo/a");
        assertThat(stub.getValue().getExternalRepoId()).isNull();
        assertThat(stub.getValue().getDefaultBranch()).isNull();
        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(VcsRepoBindingDocument.class));
    }

    @Test
    void azureReposWiperShouldBeFiledUnderAzureDevOpsAndTouchOnlyAzureReposRows() {
        VcsIntegrationDataWiper azure = new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.AZURE_REPOS);

        azure.wipe(ORG, WipeScope.wholeIntegration());

        assertThat(azure.integration()).isEqualTo(Integration.AZURE_DEVOPS);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<String> collection = ArgumentCaptor.forClass(String.class);
        verify(mongoTemplate, atLeastOnce()).remove(captor.capture(), collection.capture());
        assertThat(collection.getAllValues()).containsExactly("vcs_review_events", "vcs_pull_requests",
                "vcs_commits", "vcs_repositories");
        assertThat(captor.getAllValues()).allSatisfy(query -> {
            assertThat(query.getQueryObject().get("organizationId")).isEqualTo(ORG);
            assertThat(query.getQueryObject().get("provider")).isEqualTo(VcsProvider.AZURE_REPOS);
        });
    }

    @Test
    void azureReposStubsShouldKeepTheirGuids() {
        VcsIntegrationDataWiper azure = new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.AZURE_REPOS);
        VcsRepositoryDocument wiped = VcsRepositoryDocument.builder().id("repo-a").organizationId(ORG)
                .provider(VcsProvider.AZURE_REPOS).externalRepoId("guid-r").externalProjectId("guid-p")
                .displayName("web").defaultBranch("main").build();
        when(mongoTemplate.find(any(Query.class), eq(VcsRepositoryDocument.class))).thenReturn(List.of(wiped));
        when(mongoTemplate.find(any(Query.class), eq(VcsRepoBindingDocument.class))).thenReturn(List.of(binding("repo-a")));

        azure.wipe(ORG, WipeScope.wholeIntegration());

        ArgumentCaptor<VcsRepositoryDocument> stub = ArgumentCaptor.forClass(VcsRepositoryDocument.class);
        verify(mongoTemplate).insert(stub.capture());
        assertThat(stub.getValue().getExternalRepoId()).isEqualTo("guid-r");
        assertThat(stub.getValue().getExternalProjectId()).isEqualTo("guid-p");
        assertThat(stub.getValue().getDefaultBranch()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> inValues(Query query, String field) {
        Document condition = (Document) query.getQueryObject().get(field);
        return (List<Object>) condition.get("$in");
    }

    private static VcsRepoBindingDocument binding(String repositoryId) {
        return VcsRepoBindingDocument.builder().id("b-" + repositoryId).organizationId(ORG).projectId("proj-a")
                .repositoryId(repositoryId).provider(VcsProvider.GITHUB).active(true).build();
    }
}
