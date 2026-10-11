package andromeda.sei.ms.feature.dashboard.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsCommitDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsPullRequestDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemTransitionDocument;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis (B1 performance) — the indexes the v2 project dashboards query by are
 * created explicitly (this application does not apply {@code @CompoundIndex}): non-unique only, the
 * same keys (and name, where the declaration names one) as the declarations so they never conflict,
 * and one failing index never stops the others nor the startup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardQueryIndexInitializerTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private IndexOperations transitions;
    @Mock private IndexOperations workItems;
    @Mock private IndexOperations pullRequests;
    @Mock private IndexOperations commits;
    @Mock private IndexOperations usages;
    @Mock private IndexOperations allocations;

    @BeforeEach
    void setUp() {
        when(mongoTemplate.indexOps(WorkItemTransitionDocument.class)).thenReturn(transitions);
        when(mongoTemplate.indexOps(WorkItemDocument.class)).thenReturn(workItems);
        when(mongoTemplate.indexOps(VcsPullRequestDocument.class)).thenReturn(pullRequests);
        when(mongoTemplate.indexOps(VcsCommitDocument.class)).thenReturn(commits);
        when(mongoTemplate.indexOps(AiUsageDocument.class)).thenReturn(usages);
        when(mongoTemplate.indexOps(AiUsageAllocationDocument.class)).thenReturn(allocations);
    }

    @Test
    void should_createTheQueryIndexes_nonUnique_withTheDeclaredKeysAndNames() {
        new DashboardQueryIndexInitializer(mongoTemplate).run(null);

        assertThat(keys(transitions)).containsExactly("organizationId:1,workItemId:1");
        assertThat(keys(workItems)).containsExactly("organizationId:1,projectId:1");
        assertThat(keys(pullRequests)).containsExactly("organizationId:1,repositoryId:1,createdAt:-1",
                "organizationId:1,repositoryId:1,status:1,createdAt:-1", "organizationId:1,repositoryId:1,mergedAt:1");
        assertThat(keys(commits)).containsExactly("organizationId:1,repositoryId:1,authoredAt:1");
        assertThat(keys(usages)).containsExactly("organizationId:1,userId:1,day:1");
        assertThat(keys(allocations)).containsExactly("organizationId:1,userId:1,day:1",
                "organizationId:1,splits.workItemId:1,day:1");

        assertThat(definitions(allocations).get(0).getIndexOptions().get("name")).isEqualTo("idx_allocation_org_user_day");
        // unnamed declarations: no name, so MongoDB derives the same default name the declaration would get
        assertThat(definitions(transitions).getFirst().getIndexOptions().containsKey("name")).isFalse();
        for (IndexOperations ops : List.of(transitions, workItems, pullRequests, commits, usages, allocations)) {
            assertThat(definitions(ops)).allSatisfy(d -> assertThat(d.getIndexOptions().get("unique")).isNotEqualTo(true));
        }
    }

    @Test
    void should_keepGoing_when_oneIndexFails() {
        when(transitions.createIndex(any())).thenThrow(new DataIntegrityViolationException("conflict"));

        assertThatCode(() -> new DashboardQueryIndexInitializer(mongoTemplate).run(null)).doesNotThrowAnyException();
        verify(allocations, atLeastOnce()).createIndex(any());
    }

    private static List<IndexDefinition> definitions(IndexOperations ops) {
        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(ops, atLeastOnce()).createIndex(captor.capture());
        return captor.getAllValues();
    }

    /** Keys in index order, {@code name:direction} joined by commas. */
    private static List<String> keys(IndexOperations ops) {
        return definitions(ops).stream().map(d -> {
            Document keys = d.getIndexKeys();
            return String.join(",", keys.keySet().stream()
                    .map(k -> k + ":" + ((Number) keys.get(k)).intValue()).toList());
        }).toList();
    }
}
