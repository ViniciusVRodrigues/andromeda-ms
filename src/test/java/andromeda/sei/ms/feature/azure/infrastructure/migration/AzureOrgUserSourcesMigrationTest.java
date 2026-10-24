package andromeda.sei.ms.feature.azure.infrastructure.migration;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzureOrgUserSourcesMigrationTest {

    @Mock private MongoTemplate mongoTemplate;

    @Test
    void should_deactivateGroupsAndSetTeamSource_onlyWhereMissing_idempotently() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), any(String.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var migration = new AzureOrgUserSourcesMigration(mongoTemplate);

        migration.run(null);
        migration.run(null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(4)).updateMulti(query.capture(), update.capture(), eq("azure_org_users"));

        Document groupFilter = query.getAllValues().get(0).getQueryObject();
        List<?> clauses = groupFilter.get("$and", List.class);
        assertThat(clauses.get(0)).isEqualTo(new Document("active", true));
        Pattern pattern = Pattern.compile(((Document) clauses.get(1)).get("azureEmail", Pattern.class).pattern());
        assertThat(pattern.matcher("[Proj]\\Team").find()).isTrue();
        assertThat(pattern.matcher("ana@x.com").find()).isFalse();
        assertThat(pattern.matcher("DOM\\ana").find()).isFalse();
        Document groupOps = update.getAllValues().get(0).getUpdateObject();
        assertThat(groupOps.get("$set", Document.class)).containsEntry("active", false);
        assertThat(groupOps.get("$inc", Document.class)).containsEntry("version", 1);

        assertThat(query.getAllValues().get(1).getQueryObject())
                .isEqualTo(new Document("sources", new Document("$exists", false)));
        Document sourcesOps = update.getAllValues().get(1).getUpdateObject();
        assertThat(sourcesOps.get("$set", Document.class)).containsEntry("sources", List.of("TEAM"));
        assertThat(sourcesOps.get("$inc", Document.class)).containsEntry("version", 1);

        assertThat(query.getAllValues().get(2).getQueryObject().toJson()).isEqualTo(groupFilter.toJson());
        assertThat(query.getAllValues().get(3).getQueryObject())
                .isEqualTo(query.getAllValues().get(1).getQueryObject());
    }
}
