package andromeda.sei.ms.feature.usage.infrastructure.persistence;

import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventDraft;
import andromeda.sei.ms.feature.usage.domain.FunnelFlow;
import andromeda.sei.ms.feature.usage.domain.FunnelStage;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunnelEventRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private static final String FUNNEL = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";

    @Mock private MongoTemplate mongoTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void should_insertTheBatchAnonymously_mappingEveryField() {
        FunnelEvent e = FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP,
                new FunnelEventDraft(FunnelStage.STEP_TIME, NOW, "senha", 41_200L, null, "md"), NOW);

        new FunnelEventRepositoryImpl(mongoTemplate).insertAll(List.of(e));

        ArgumentCaptor<Collection<FunnelEventDocument>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate).insert(captor.capture(), eq(FunnelEventDocument.class));
        FunnelEventDocument d = List.copyOf(captor.getValue()).get(0);
        assertThat(d.getFunnelId()).isEqualTo(FUNNEL);
        assertThat(d.getFlow()).isEqualTo("signup");
        assertThat(d.getStage()).isEqualTo("STEP_TIME");
        assertThat(d.getStep()).isEqualTo("senha");
        assertThat(d.getVisibleMs()).isEqualTo(41_200L);
        assertThat(d.getViewport()).isEqualTo("md");
        assertThat(d.getOrganizationId()).isNull();
        assertThat(d.getUserId()).isNull();
        assertThat(d.getLinkedAt()).isNull();
        assertThat(d.isActive()).isTrue();
        assertThat(d.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_notTouchMongo_when_theBatchIsEmpty() {
        new FunnelEventRepositoryImpl(mongoTemplate).insertAll(List.of());

        verify(mongoTemplate, never()).insert(anyCollection(), any(Class.class));
    }

    @Test
    void should_linkOnlyAnonymousEventsOfTheFunnelAndFlowInsideTheWindow() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(FunnelEventDocument.class)))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null));
        Instant since = NOW.minusSeconds(7 * 86_400L);

        long changed = new FunnelEventRepositoryImpl(mongoTemplate)
                .linkToAccount(FUNNEL, FunnelFlow.INVITE, "org-1", "user-1", since, NOW);

        assertThat(changed).isEqualTo(3);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(FunnelEventDocument.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q).containsEntry("funnelId", FUNNEL).containsEntry("flow", "invite");
        assertThat(q).containsKey("userId");          // userId:null is what stops a second link
        assertThat(q.get("userId")).isNull();
        assertThat((Document) q.get("createdAt")).containsEntry("$gte", since);
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set).containsEntry("organizationId", "org-1").containsEntry("userId", "user-1")
                .containsEntry("linkedAt", NOW);
    }

    @Test
    void should_streamByOrganizationAndPeriod_sortedByOccurredAt() {
        FunnelEventDocument doc = FunnelEventDocument.builder().funnelId(FUNNEL).flow("signup").stage("OPEN")
                .organizationId("org-1").userId("user-1").occurredAt(NOW).createdAt(NOW).build();
        when(mongoTemplate.stream(any(Query.class), eq(FunnelEventDocument.class))).thenReturn(Stream.of(doc));
        Instant from = NOW.minusSeconds(3600);

        List<FunnelEvent> out;
        try (Stream<FunnelEvent> s = new FunnelEventRepositoryImpl(mongoTemplate).streamByOrganization("org-1", from, NOW)) {
            out = s.toList();
        }

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getFlow()).isEqualTo(FunnelFlow.SIGNUP);
        assertThat(out.get(0).getStage()).isEqualTo(FunnelStage.OPEN);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).stream(query.capture(), eq(FunnelEventDocument.class));
        Document q = query.getValue().getQueryObject();
        assertThat(q).containsEntry("organizationId", "org-1");
        Document range = (Document) q.get("occurredAt");
        assertThat(range).containsEntry("$gte", from).containsEntry("$lt", NOW);   // [from, to)
        assertThat(query.getValue().getSortObject()).containsEntry("occurredAt", 1);
    }

    @Test
    void should_declareTheCollectionAndItsIndexes() throws Exception {
        assertThat(FunnelEventDocument.class.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class)
                .collection()).isEqualTo("funnel_events");
        CompoundIndex[] compound = FunnelEventDocument.class.getAnnotation(CompoundIndexes.class).value();
        assertThat(compound).hasSize(1);
        assertThat(compound[0].name()).isEqualTo("ix_funnel_id_occurred");
        assertThat(compound[0].def()).isEqualTo("{'funnelId': 1, 'occurredAt': 1}");
        Field createdAt = FunnelEventDocument.class.getDeclaredField("createdAt");
        assertThat(createdAt.getAnnotation(Indexed.class).expireAfter()).isEqualTo("180d");
        assertThat(FunnelEventDocument.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }

    @Test
    void should_haveNoFieldForIpUserAgentOrPersonalData() {
        assertThat(List.of(FunnelEventDocument.class.getDeclaredFields())).extracting(Field::getName)
                .doesNotContain("ip", "remoteAddr", "userAgent", "email", "name", "text", "message");
    }
}
