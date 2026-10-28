package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** dashboards-numeros D3: the upsert writes {@code editor}, on insert and on the update of the same day. */
@ExtendWith(MockitoExtension.class)
class AiUsageRepositoryImplUpsertEditorTest {

    @Mock private AiUsageMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;

    @Test
    void should_setEditor_when_upsertingAUsageDay() {
        AIUsage usage = usage("cursor");
        var repo = new AiUsageRepositoryImpl(mongoRepository, new AiUsageDocumentMapper(), mongoTemplate);

        repo.upsert(usage);
        repo.upsert(usage("vscode"));

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).upsert(any(Query.class), update.capture(), eq(AiUsageDocument.class));
        assertThat(update.getAllValues().get(0).getUpdateObject().get("$set")).hasFieldOrPropertyWithValue("editor", "cursor");
        assertThat(update.getAllValues().get(1).getUpdateObject().get("$set")).hasFieldOrPropertyWithValue("editor", "vscode");
    }

    private static AIUsage usage(String editor) {
        return new AIUsage(null, "org-1", "u1", null, "ds-1", LocalDate.parse("2026-08-05"), "claude", "ws", null,
                "m", null, 10, 5, 1, editor, null, null, null, true, null, null, null);
    }
}
