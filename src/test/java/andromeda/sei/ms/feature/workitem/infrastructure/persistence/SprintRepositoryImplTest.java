package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class SprintRepositoryImplTest {

    @Autowired
    private SprintMongoRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private SprintRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String TEAM_ID = "team-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new SprintRepositoryImpl(mongoRepository, new SprintDocumentMapper(), mongoTemplate);
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldUpsertByCompositeKey() {
        Sprint sprint = sprint("iter-1");
        repository.save(sprint);
        repository.save(sprint);

        assertThat(mongoRepository.findAll()).hasSize(1);
    }

    @Test
    void findByOrganizationIdAndExternalIdAndTeamId_shouldReturnExisting() {
        repository.save(sprint("iter-1"));

        var result = repository.findByOrganizationIdAndExternalIdAndTeamId(ORG_ID, "iter-1", TEAM_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("iter-1");
    }

    @Test
    void save_shouldUpdateMutableFieldsOnly() {
        Sprint first = sprint("iter-1");
        Sprint saved = repository.save(first);
        Instant originalCreatedAt = saved.getCreatedAt();

        Sprint updated = sprint("iter-1");
        updated.setName("Sprint 2 Updated");
        repository.save(updated);

        var result = repository.findByOrganizationIdAndExternalIdAndTeamId(ORG_ID, "iter-1", TEAM_ID).orElseThrow();
        assertThat(result.getName()).isEqualTo("Sprint 2 Updated");
        assertThat(result.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    private Sprint sprint(String externalId) {
        return new Sprint(null, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                externalId, TEAM_ID, "Team A", "Sprint 1", "\\Project\\Sprint 1",
                null, null, Sprint.TimeFrame.CURRENT, true, null, NOW, NOW);
    }
}
