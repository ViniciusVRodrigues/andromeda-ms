package andromeda.sei.ms.feature.azure.infrastructure.client;

import andromeda.sei.ms.feature.azure.domain.WiqlFilter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WiqlQueryBuilderTest {

    @Test
    void shouldBuildQueryWithNoFilters() {
        String wiql = WiqlQueryBuilder.build(null, null, null);

        assertThat(wiql).isEqualTo("SELECT [System.Id] FROM WorkItems ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeChangedDateFilterWhenSinceProvided() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");

        String wiql = WiqlQueryBuilder.build(since, null, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '2026-01-01T00:00:00Z' ORDER BY [System.Id] ASC");
    }

    /**
     * Azure DevOps rejects WIQL DateTime literals carrying sub-second digits with
     * HTTP 400, so the bound must be truncated to second precision.
     */
    @Test
    void shouldTruncateSubSecondPrecisionFromChangedDateFilter() {
        Instant since = Instant.parse("2026-08-12T12:34:56.789123Z");

        String wiql = WiqlQueryBuilder.build(since, null, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '2026-08-12T12:34:56Z' ORDER BY [System.Id] ASC");
        assertThat(wiql).doesNotContain("789");
    }

    @Test
    void shouldFormatChangedDateBoundInUtcRegardlessOfSourceOffset() {
        Instant since = Instant.parse("2026-08-12T23:59:59.999Z");

        String wiql = WiqlQueryBuilder.build(since, null, null);

        assertThat(wiql).contains("[System.ChangedDate] >= '2026-08-12T23:59:59Z'");
    }

    @Test
    void shouldIncludeIdLowerBoundWhenLastSeenIdProvided() {
        String wiql = WiqlQueryBuilder.build(null, 100, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.Id] > 100 ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldCombineDateAndPaginationFiltersWithAnd() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");

        String wiql = WiqlQueryBuilder.build(since, 100, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '2026-01-01T00:00:00Z' AND [System.Id] > 100 ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeWorkItemTypeInClauseWhenTypesConfigured() {
        WiqlFilter filter = new WiqlFilter(List.of("Bug", "User Story"), null, null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.WorkItemType] IN ('Bug', 'User Story') ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeAreaPathUnderClauseWhenAreaConfigured() {
        WiqlFilter filter = new WiqlFilter(null, "MyProject\\TeamA", null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.AreaPath] UNDER 'MyProject\\TeamA' ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeIterationPathUnderClauseWhenIterationConfigured() {
        WiqlFilter filter = new WiqlFilter(null, null, "MyProject\\Sprint 1");

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.IterationPath] UNDER 'MyProject\\Sprint 1' ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldCombineAllFilterConditionsWithAnd() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");
        WiqlFilter filter = new WiqlFilter(List.of("Bug"), "MyProject\\TeamA", "MyProject\\Sprint 1");

        String wiql = WiqlQueryBuilder.build(since, 100, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.ChangedDate] >= '2026-01-01T00:00:00Z'"
                        + " AND [System.Id] > 100"
                        + " AND [System.WorkItemType] IN ('Bug')"
                        + " AND [System.AreaPath] UNDER 'MyProject\\TeamA'"
                        + " AND [System.IterationPath] UNDER 'MyProject\\Sprint 1'"
                        + " ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIgnoreEmptyWorkItemTypesList() {
        WiqlFilter filter = new WiqlFilter(List.of(), null, null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo("SELECT [System.Id] FROM WorkItems ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIgnoreBlankAreaAndIterationPaths() {
        WiqlFilter filter = new WiqlFilter(null, "  ", "");

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo("SELECT [System.Id] FROM WorkItems ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldEscapeSingleQuotesInWorkItemTypeAndPaths() {
        WiqlFilter filter = new WiqlFilter(List.of("O'Brien Type"), "Project\\O'Brien", null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).contains("'O''Brien Type'");
        assertThat(wiql).contains("UNDER 'Project\\O''Brien'");
    }
}
