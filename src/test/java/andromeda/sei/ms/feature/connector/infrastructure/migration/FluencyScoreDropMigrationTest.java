package andromeda.sei.ms.feature.connector.infrastructure.migration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.client.MongoCollection;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FluencyScoreDropMigrationTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final FluencyScoreDropMigration migration = new FluencyScoreDropMigration(mongoTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void dropsTheCollectionAndLogsTheCount() {
        Logger logger = (Logger) LoggerFactory.getLogger(FluencyScoreDropMigration.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MongoCollection<org.bson.Document> collection = mock(MongoCollection.class);
            when(mongoTemplate.collectionExists("fluency_scores")).thenReturn(true);
            when(mongoTemplate.getCollection("fluency_scores")).thenReturn(collection);
            when(collection.countDocuments()).thenReturn(42L);

            migration.run(null);

            verify(mongoTemplate).dropCollection("fluency_scores");
            assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().contains("42"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void doesNothingAndDoesNotFailWithoutTheCollection() {
        when(mongoTemplate.collectionExists("fluency_scores")).thenReturn(false);

        assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();

        verify(mongoTemplate, never()).dropCollection(anyString());
        verify(mongoTemplate).collectionExists("fluency_scores");
        verifyNoMoreInteractions(mongoTemplate);
    }
}
