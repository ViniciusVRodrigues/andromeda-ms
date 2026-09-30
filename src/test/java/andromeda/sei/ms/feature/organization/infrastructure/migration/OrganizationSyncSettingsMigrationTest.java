package andromeda.sei.ms.feature.organization.infrastructure.migration;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code @DataMongoTest} esta indisponivel neste codebase (Boot 4 + flapdoodle; ver os
 * {@code *RepositoryImplTest} desabilitados), entao a migracao e verificada contra o
 * {@link MongoTemplate} mockado, no mesmo estilo de {@code WorkItemEffortBackfillMigrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationSyncSettingsMigrationTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private ApplicationArguments applicationArguments;

    @InjectMocks
    private OrganizationSyncSettingsMigration migration;

    @Test
    void shouldOnlyTouchOrganizationsWithoutSyncSettings() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq("organizations")))
                .thenReturn(UpdateResult.acknowledged(2, 2L, null));

        migration.run(applicationArguments);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateMulti(query.capture(), any(Update.class), eq("organizations"));
        String json = query.getValue().getQueryObject().toJson();
        assertThat(json).contains("\"syncSettings\"").contains("\"$exists\": false");
    }

    @Test
    void shouldMigrateExistingOrganizationsToDailyWithNinetyDayBackfill() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq("organizations")))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        migration.run(applicationArguments);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(any(Query.class), update.capture(), eq("organizations"));
        String json = update.getValue().getUpdateObject().toJson();
        assertThat(json).contains("\"scheduledSync\": \"DAILY\"").contains("\"backfillDays\": 90");
    }
}
