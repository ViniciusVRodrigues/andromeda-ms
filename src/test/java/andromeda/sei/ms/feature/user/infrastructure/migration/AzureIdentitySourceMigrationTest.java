package andromeda.sei.ms.feature.user.infrastructure.migration;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contra um MongoTemplate simulado: a idempotencia esta no filtro e nos operadores. */
@ExtendWith(MockitoExtension.class)
class AzureIdentitySourceMigrationTest {

    @Mock private MongoTemplate mongoTemplate;

    @Test
    void should_markAdmin_onlyWhereAzureIdPresentAndSourceMissing_idempotently() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), any(String.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var migration = new AzureIdentitySourceMigration(mongoTemplate);

        migration.run(null);
        migration.run(null); // segunda execucao: mesmo filtro, que nao casa mais nada

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).updateMulti(query.capture(), update.capture(), eq("users"));

        Document filter = query.getAllValues().get(0).getQueryObject();
        List<?> clauses = filter.get("$and", List.class);
        assertThat(clauses).hasSize(2);
        assertThat(clauses.get(0)).isEqualTo(new Document("azureId", new Document("$ne", null)));
        assertThat(clauses.get(1)).isEqualTo(new Document("azureIdentitySource", new Document("$exists", false)));
        Document ops = update.getAllValues().get(0).getUpdateObject();
        assertThat(ops.get("$set", Document.class)).containsEntry("azureIdentitySource", "ADMIN");
        assertThat(ops.get("$inc", Document.class)).containsEntry("version", 1);
        assertThat(query.getAllValues().get(1).getQueryObject()).isEqualTo(filter);
    }
}
