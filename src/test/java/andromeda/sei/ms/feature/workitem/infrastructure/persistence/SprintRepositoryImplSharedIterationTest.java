package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * metrics-correctness 1.3 (shared iterations) — against a mocked {@link MongoTemplate} (the
 * {@code @DataMongoTest} suite is disabled under Boot 4): the project's current sprint must not be
 * whichever twin {@code findOne} happens to return, and the path lookup returns every twin.
 */
@ExtendWith(MockitoExtension.class)
class SprintRepositoryImplSharedIterationTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final String PATH = "Proj\\Sprint 25";
    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    @Mock private SprintMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;

    private SprintRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new SprintRepositoryImpl(mongoRepository, new SprintDocumentMapper(), mongoTemplate);
    }

    @Test
    void should_returnCanonicalTwin_asCurrentSprint_whatever_theStorageOrder() {
        SprintDocument teamB = doc("s-b", "team-b", T0.plusSeconds(60));
        SprintDocument teamA = doc("s-a", "team-a", T0);
        when(mongoTemplate.find(any(Query.class), eq(SprintDocument.class))).thenReturn(List.of(teamB, teamA));

        assertThat(repository.findCurrentSprintByOrganizationIdAndProjectId(ORG, PROJECT))
                .map(Sprint::getId).contains("s-a");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(SprintDocument.class));
        Document filter = query.getValue().getQueryObject();
        assertThat(filter).containsEntry("organizationId", ORG).containsEntry("projectId", PROJECT)
                .containsEntry("timeFrame", Sprint.TimeFrame.CURRENT).containsEntry("active", true);
    }

    @Test
    void should_returnEmpty_when_projectHasNoCurrentSprint() {
        when(mongoTemplate.find(any(Query.class), eq(SprintDocument.class))).thenReturn(List.of());

        assertThat(repository.findCurrentSprintByOrganizationIdAndProjectId(ORG, PROJECT)).isEmpty();
    }

    @Test
    void should_returnEveryTwin_forAPath_scopedByOrganizationAndProject() {
        when(mongoRepository.findAllByOrganizationIdAndProjectIdAndPathAndActiveTrue(ORG, PROJECT, PATH))
                .thenReturn(List.of(doc("s-a", "team-a", T0), doc("s-b", "team-b", T0)));

        assertThat(repository.findAllByOrganizationIdAndProjectIdAndPath(ORG, PROJECT, PATH))
                .extracting(Sprint::getTeamId).containsExactly("team-a", "team-b");
    }

    private static SprintDocument doc(String id, String teamId, Instant createdAt) {
        return SprintDocument.builder().id(id).organizationId(ORG).projectId(PROJECT)
                .source(WorkItem.Source.AZURE_DEVOPS).externalId("iter-25").teamId(teamId).teamName(teamId)
                .name("Sprint 25").path(PATH).startDate(T0).endDate(T0.plusSeconds(1_209_600))
                .timeFrame(Sprint.TimeFrame.CURRENT).active(true).createdAt(createdAt).updatedAt(createdAt)
                .version(0L).build();
    }
}
