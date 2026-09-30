package andromeda.sei.ms.feature.github.infrastructure.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MongoTemplate mockado, como as demais migracoes (@DataMongoTest nao roda sob Boot 4 aqui). */
@ExtendWith(MockitoExtension.class)
class RepoMetricsDropMigrationTest {

    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks
    private RepoMetricsDropMigration migration;

    @Test
    void should_dropCollection_when_itExists() {
        when(mongoTemplate.collectionExists("repo_metrics")).thenReturn(true);

        migration.run(null);

        verify(mongoTemplate).dropCollection("repo_metrics");
    }

    @Test
    void should_doNothing_when_collectionIsAlreadyGone() {
        when(mongoTemplate.collectionExists("repo_metrics")).thenReturn(false);

        migration.run(null);

        verify(mongoTemplate, never()).dropCollection(anyString());
    }
}
