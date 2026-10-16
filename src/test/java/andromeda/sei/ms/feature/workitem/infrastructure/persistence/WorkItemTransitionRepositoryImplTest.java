package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class WorkItemTransitionRepositoryImplTest {

    @Autowired
    private WorkItemTransitionMongoRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private WorkItemTransitionRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String WORK_ITEM_ID = "wi-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new WorkItemTransitionRepositoryImpl(
                mongoRepository, new WorkItemTransitionDocumentMapper(), mongoTemplate);
        mongoRepository.deleteAll();
    }

    @Test
    void upsertByCompositeKey_shouldNotCreateDuplicate() {
        WorkItemTransition t = transition("Active");
        repository.upsertByCompositeKey(t);
        repository.upsertByCompositeKey(t);

        assertThat(mongoRepository.findAll()).hasSize(1);
    }

    @Test
    void findAllByWorkItemId_shouldReturnAllTransitions() {
        repository.upsertByCompositeKey(transition("Active"));
        repository.upsertByCompositeKey(transition("Closed"));

        List<WorkItemTransition> results = repository.findAllByWorkItemId(WORK_ITEM_ID);

        assertThat(results).hasSize(2);
    }

    @Test
    void existsByWorkItemId_shouldReturnTrueWhenTransitionsExist() {
        assertThat(repository.existsByWorkItemId(WORK_ITEM_ID)).isFalse();
        repository.upsertByCompositeKey(transition("Active"));
        assertThat(repository.existsByWorkItemId(WORK_ITEM_ID)).isTrue();
    }

    private WorkItemTransition transition(String toRawStatus) {
        return new WorkItemTransition(null, ORG_ID, WORK_ITEM_ID,
                null, toRawStatus, WorkItem.ResolvedStatus.IN_PROGRESS,
                NOW, NOW, true, null, NOW, NOW);
    }
}
