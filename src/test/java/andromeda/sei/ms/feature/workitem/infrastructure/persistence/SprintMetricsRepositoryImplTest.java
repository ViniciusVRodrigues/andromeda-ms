package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 — needs rewrite as @SpringBootTest slice")
class SprintMetricsRepositoryImplTest {

    @Autowired
    private SprintMetricsMongoRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private SprintMetricsRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SPRINT_ID = "sprint-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        SprintMetricsDocumentMapper mapper = new SprintMetricsDocumentMapper();
        repository = new SprintMetricsRepositoryImpl(mongoRepository, mapper, mongoTemplate);
        mongoRepository.deleteAll();
    }

    @Test
    void upsertsByCompositeKey() {
        SprintMetrics metrics = metrics(SPRINT_ID, 10.0);

        SprintMetrics saved = repository.save(metrics);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVelocity()).isEqualTo(10.0);
        assertThat(mongoRepository.count()).isEqualTo(1);
    }

    @Test
    void overwritesOnSecondCall() {
        repository.save(metrics(SPRINT_ID, 10.0));
        repository.save(metrics(SPRINT_ID, 20.0));

        assertThat(mongoRepository.count()).isEqualTo(1);
        SprintMetrics found = repository
                .findByOrganizationIdAndSprintIdAndSource(ORG_ID, SPRINT_ID, WorkItem.Source.AZURE_DEVOPS)
                .orElseThrow();
        assertThat(found.getVelocity()).isEqualTo(20.0);
    }

    @Test
    void findAllByOrgAndProject() {
        repository.save(metrics("sprint-a", 5.0));
        repository.save(metrics("sprint-b", 8.0));

        List<SprintMetrics> all = repository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID);
        assertThat(all).hasSize(2);
    }

    private SprintMetrics metrics(String sprintId, Double velocity) {
        return new SprintMetrics(null, ORG_ID, PROJECT_ID, sprintId,
                WorkItem.Source.AZURE_DEVOPS, velocity, null, null,
                0, 0, 0, NOW, true, null, null, null);
    }
}
