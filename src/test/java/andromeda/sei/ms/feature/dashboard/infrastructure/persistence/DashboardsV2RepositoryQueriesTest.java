package andromeda.sei.ms.feature.dashboard.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.infrastructure.persistence.ConnectorTokenDocument;
import andromeda.sei.ms.feature.connector.infrastructure.persistence.FluencyScoreDocument;
import andromeda.sei.ms.feature.connector.infrastructure.persistence.FluencyScoreDocumentMapper;
import andromeda.sei.ms.feature.connector.infrastructure.persistence.FluencyScoreRepositoryImpl;
import andromeda.sei.ms.feature.provideraccount.infrastructure.persistence.UserProviderAccountDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * dashboards-customizaveis 1.11/1.12 — the organization-wide finders added for the v2 dashboards
 * without a database (persistence slices do not run under Boot 4 here): the derived method names
 * resolve to real document properties (a typo would otherwise only fail at application start), and
 * the fluency query is scoped by the organization and active rows.
 */
class DashboardsV2RepositoryQueriesTest {

    @Test
    void should_resolveTheDerivedFinderNames_toDocumentProperties() {
        assertThatCode(() -> new PartTree("findAllByOrganizationId", ConnectorTokenDocument.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PartTree("findAllByOrganizationIdAndActiveTrue", UserProviderAccountDocument.class))
                .doesNotThrowAnyException();
    }

    @Test
    void should_readTheActiveFluencyScoresOfTheOrganization() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        new FluencyScoreRepositoryImpl(mongoTemplate, new FluencyScoreDocumentMapper()).findAllByOrganizationId("org-1");

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(FluencyScoreDocument.class));
        assertThat(captor.getValue().getQueryObject().get("organizationId")).isEqualTo("org-1");
        assertThat(captor.getValue().getQueryObject().get("active")).isEqualTo(true);
    }
}
