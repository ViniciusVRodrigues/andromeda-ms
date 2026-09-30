package andromeda.sei.ms.feature.azure.infrastructure.migration;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Task 1.3: OAuth connections stored with a non-ADO URL (the Entra issuer) are flagged for a fix. */
@ExtendWith(MockitoExtension.class)
class AzureOrgUrlMigrationTest {

    @Mock private MongoTemplate mongoTemplate;
    @InjectMocks private AzureOrgUrlMigration migration;

    @Test
    void shouldFlagOnlyActiveOAuthConnectionsWithANonAdoUrl() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("azure_connections"))).thenReturn(List.of(
                new Document("_id", "issuer").append("azureOrgUrl", "https://login.microsoftonline.com/t/v2.0"),
                new Document("_id", "fallback").append("azureOrgUrl", "https://dev.azure.com"),
                new Document("_id", "good").append("azureOrgUrl", "https://dev.azure.com/acme"),
                new Document("_id", "done").append("azureOrgUrl", "https://login.microsoftonline.com/t/v2.0")
                        .append("lastErrorCode", "AZURE_ORG_URL_INVALID")));

        migration.run(null);

        ArgumentCaptor<Query> scope = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(scope.capture(), eq(Document.class), eq("azure_connections"));
        assertThat(scope.getValue().getQueryObject().get("active")).isEqualTo(true);
        assertThat(scope.getValue().getQueryObject().toJson()).contains("OAUTH").contains("null");

        ArgumentCaptor<Query> target = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).updateFirst(target.capture(), update.capture(),
                eq("azure_connections"));
        assertThat(target.getAllValues()).extracting(q -> q.getQueryObject().get("_id"))
                .containsExactly("issuer", "fallback");
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("status")).isEqualTo("ERROR");
        assertThat(set.get("lastErrorCode")).isEqualTo("AZURE_ORG_URL_INVALID");
    }

    @Test
    void shouldBeANoOpWhenEverythingIsValid() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("azure_connections"))).thenReturn(List.of(
                new Document("_id", "good").append("azureOrgUrl", "https://acme.visualstudio.com/")));

        migration.run(null);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }
}
