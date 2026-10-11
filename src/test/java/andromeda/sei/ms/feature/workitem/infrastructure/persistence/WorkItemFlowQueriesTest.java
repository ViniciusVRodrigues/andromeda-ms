package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.13 — the bounded work item reads of the team flow are always scoped by
 * organization and project, and the sprint batch never reaches Mongo without ids. (The
 * {@code @DataMongoTest} suite of this repository is disabled under Boot 4; this proves the adapter's
 * contract with the Mongo repository.)
 */
@ExtendWith(MockitoExtension.class)
class WorkItemFlowQueriesTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "project-1";
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-15T00:00:00Z");

    @Mock private WorkItemMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;

    private WorkItemRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new WorkItemRepositoryImpl(mongoRepository, new WorkItemDocumentMapper(), mongoTemplate);
    }

    @Test
    void should_readSeveralSprints_inOneScopedQuery() {
        when(mongoRepository.findAllByOrganizationIdAndProjectIdAndSprintIdInAndActiveTrue(ORG, PROJECT, Set.of("s1", "s2")))
                .thenReturn(List.of(doc("w1", "s1")));

        List<WorkItem> found = repository.findActiveByOrganizationIdAndProjectIdAndSprintIdIn(ORG, PROJECT, Set.of("s1", "s2"));

        assertThat(found).extracting(WorkItem::getId).containsExactly("w1");
    }

    @Test
    void should_notQuery_withoutSprintIds() {
        assertThat(repository.findActiveByOrganizationIdAndProjectIdAndSprintIdIn(ORG, PROJECT, List.of())).isEmpty();
        verify(mongoRepository, never()).findAllByOrganizationIdAndProjectIdAndSprintIdInAndActiveTrue(anyString(),
                anyString(), any());
    }

    @Test
    void should_readCompletedInRange_scopedByOrganizationAndProject() {
        when(mongoRepository.findActiveCompletedInRange(ORG, PROJECT, FROM, TO)).thenReturn(List.of(doc("w2", null)));

        List<WorkItem> found = repository.findActiveByOrganizationIdAndProjectIdAndCompletedAtInRange(ORG, PROJECT, FROM, TO);

        assertThat(found).extracting(WorkItem::getId).containsExactly("w2");
    }

    private static WorkItemDocument doc(String id, String sprintId) {
        return WorkItemDocument.builder().id(id).organizationId(ORG).projectId(PROJECT).sprintId(sprintId)
                .externalId("ext-" + id).active(true).version(0L).build();
    }
}
