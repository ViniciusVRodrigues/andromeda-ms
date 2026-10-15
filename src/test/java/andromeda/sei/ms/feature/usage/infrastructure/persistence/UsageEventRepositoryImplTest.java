package andromeda.sei.ms.feature.usage.infrastructure.persistence;

import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventDraft;
import andromeda.sei.ms.feature.usage.domain.UsageEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageEventRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");

    @Mock private MongoTemplate mongoTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void should_insertTheBatchInOneCall_mappingEveryField() {
        UsageEvent click = UsageEvent.fromClient("org-A", "user-1",
                UsageEventDraft.builder(UsageEventType.CLICK, NOW)
                        .sessionId("3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b").viewId("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d")
                        .screen("backlog").viewport("md").element("button").area("main").label("Atualizar").build(), NOW);
        UsageEvent error = UsageEvent.fromClient("org-A", "user-1",
                UsageEventDraft.builder(UsageEventType.ERROR, NOW)
                        .sessionId("3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b").viewId("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d")
                        .screen("integracoes").origin("api").status(500).code("AZURE_SYNC_FAILED").build(), NOW);

        new UsageEventRepositoryImpl(mongoTemplate).insertAll(List.of(click, error));

        ArgumentCaptor<Collection<UsageEventDocument>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate).insert(captor.capture(), org.mockito.ArgumentMatchers.eq(UsageEventDocument.class));
        List<UsageEventDocument> docs = List.copyOf(captor.getValue());
        assertThat(docs).hasSize(2);
        UsageEventDocument c = docs.get(0);
        assertThat(c.getOrganizationId()).isEqualTo("org-A");
        assertThat(c.getUserId()).isEqualTo("user-1");
        assertThat(c.getType()).isEqualTo("CLICK");
        assertThat(c.getScreen()).isEqualTo("backlog");
        assertThat(c.getElement()).isEqualTo("button");
        assertThat(c.getArea()).isEqualTo("main");
        assertThat(c.getLabel()).isEqualTo("Atualizar");
        assertThat(c.getViewport()).isEqualTo("md");
        assertThat(c.getOccurredAt()).isEqualTo(NOW);
        assertThat(c.getCreatedAt()).isEqualTo(NOW);
        assertThat(c.isActive()).isTrue();
        UsageEventDocument e = docs.get(1);
        assertThat(e.getOrigin()).isEqualTo("api");
        assertThat(e.getStatus()).isEqualTo(500);
        assertThat(e.getCode()).isEqualTo("AZURE_SYNC_FAILED");
    }

    @Test
    void should_notTouchMongo_when_theBatchIsEmpty() {
        new UsageEventRepositoryImpl(mongoTemplate).insertAll(List.of());

        verify(mongoTemplate, never()).insert(anyCollection(), any(Class.class));
    }

    @Test
    void should_declareTheCollectionAndItsRetentionIndexes() throws Exception {
        assertThat(UsageEventDocument.class.getAnnotation(Document.class).collection()).isEqualTo("usage_events");

        CompoundIndex[] compound = UsageEventDocument.class.getAnnotation(CompoundIndexes.class).value();
        assertThat(compound).hasSize(1);
        assertThat(compound[0].name()).isEqualTo("ix_usage_org_occurred");
        assertThat(compound[0].def()).contains("organizationId").contains("occurredAt");

        Field createdAt = UsageEventDocument.class.getDeclaredField("createdAt");
        assertThat(createdAt.getAnnotation(Indexed.class).expireAfter()).isEqualTo("180d");

        assertThat(UsageEventDocument.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void should_notHaveAnyFieldForMessageStackOrUrl() {
        assertThat(List.of(UsageEventDocument.class.getDeclaredFields()))
                .extracting(Field::getName)
                .doesNotContain("message", "stack", "url", "path", "error");
    }
}
