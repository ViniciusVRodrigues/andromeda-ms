package andromeda.sei.ms.feature.dashboard.infrastructure.persistence;

import andromeda.sei.ms.feature.connector.infrastructure.persistence.ConnectorTokenDocument;
import andromeda.sei.ms.feature.provideraccount.infrastructure.persistence.UserProviderAccountDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * dashboards-customizaveis 1.11/1.12 — the organization-wide finders added for the v2 dashboards
 * without a database (persistence slices do not run under Boot 4 here): the derived method names
 * resolve to real document properties (a typo would otherwise only fail at application start).
 */
class DashboardsV2RepositoryQueriesTest {

    @Test
    void should_resolveTheDerivedFinderNames_toDocumentProperties() {
        assertThatCode(() -> new PartTree("findAllByOrganizationId", ConnectorTokenDocument.class))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PartTree("findAllByOrganizationIdAndActiveTrue", UserProviderAccountDocument.class))
                .doesNotThrowAnyException();
    }
}
