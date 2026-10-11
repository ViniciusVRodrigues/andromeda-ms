package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
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
 * dashboards-customizaveis 1.9 — the batch transition query is always scoped by organization and
 * active, and never reaches Mongo without ids. (The {@code @DataMongoTest} suite of this repository
 * is disabled under Boot 4; this proves the adapter's contract with the derived query.)
 */
@ExtendWith(MockitoExtension.class)
class WorkItemTransitionBatchQueryTest {

    private static final Instant AT = Instant.parse("2026-10-01T00:00:00Z");

    @Mock private WorkItemTransitionMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;

    @Test
    void should_queryByOrganizationAndIds_activeOnly() {
        WorkItemTransitionRepositoryImpl repository = new WorkItemTransitionRepositoryImpl(mongoRepository,
                new WorkItemTransitionDocumentMapper(), mongoTemplate);
        WorkItemTransitionDocument doc = WorkItemTransitionDocument.builder().id("t1").organizationId("org-1")
                .workItemId("w1").toRawStatus("Done").toResolvedStatus(WorkItem.ResolvedStatus.DONE)
                .occurredAt(AT).active(true).version(0L).build();
        when(mongoRepository.findAllByOrganizationIdAndWorkItemIdInAndActiveTrue("org-1", Set.of("w1")))
                .thenReturn(List.of(doc));

        List<WorkItemTransition> found = repository.findActiveByOrganizationIdAndWorkItemIdIn("org-1", Set.of("w1"));

        assertThat(found).extracting(WorkItemTransition::getWorkItemId).containsExactly("w1");
        assertThat(found.getFirst().getToResolvedStatus()).isEqualTo(WorkItem.ResolvedStatus.DONE);
    }

    @Test
    void should_notQuery_withoutIds() {
        WorkItemTransitionRepositoryImpl repository = new WorkItemTransitionRepositoryImpl(mongoRepository,
                new WorkItemTransitionDocumentMapper(), mongoTemplate);

        assertThat(repository.findActiveByOrganizationIdAndWorkItemIdIn("org-1", List.of())).isEmpty();
        verify(mongoRepository, never()).findAllByOrganizationIdAndWorkItemIdInAndActiveTrue(anyString(), any());
    }
}
