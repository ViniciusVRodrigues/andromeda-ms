package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.user.domain.User;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLastLoginTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private static final Instant LOGIN_AT = Instant.parse("2026-10-21T09:30:00Z");

    @Mock private UserMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;

    private final UserDocumentMapper mapper = new UserDocumentMapper();

    private User user() {
        return new User("user-1", "org-A", "ana@acme.com", "hash", "Ana", "role-1", null, null, null, null,
                null, true, true, 3L, NOW, NOW);
    }

    @Test
    void mapper_shouldCarryLastLoginAtInBothDirections() {
        User user = user();
        user.setLastLoginAt(LOGIN_AT);

        UserDocument doc = mapper.toDocument(user);
        assertThat(doc.getLastLoginAt()).isEqualTo(LOGIN_AT);

        User back = mapper.toDomain(doc);
        assertThat(back.getLastLoginAt()).isEqualTo(LOGIN_AT);
    }

    @Test
    void mapper_shouldKeepLastLoginAtNull_whenAbsent() {
        assertThat(mapper.toDomain(mapper.toDocument(user())).getLastLoginAt()).isNull();
    }

    @Test
    void recordLogin_shouldSetOnlyLastLoginAt_atomically_filteredByIdOrgAndActive() {
        UserRepositoryImpl repository = new UserRepositoryImpl(mongoRepository, mapper, mongoTemplate);

        repository.recordLogin("user-1", "org-A", LOGIN_AT);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(UserDocument.class));
        verify(mongoRepository, never()).save(any());

        Document filter = query.getValue().getQueryObject();
        assertThat(filter).containsEntry("_id", "user-1").containsEntry("organizationId", "org-A").containsEntry("active", true);

        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set).containsOnlyKeys("lastLoginAt");
        assertThat(set.get("lastLoginAt")).isEqualTo(LOGIN_AT);
        assertThat(update.getValue().getUpdateObject()).containsOnlyKeys("$set");
    }
}
