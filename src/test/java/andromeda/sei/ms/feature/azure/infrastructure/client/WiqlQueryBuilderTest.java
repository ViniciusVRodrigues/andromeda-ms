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

        assertThat(wiql).isEqualTo("SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeAuthorizedDateFilterWhenSinceProvided() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");

        String wiql = WiqlQueryBuilder.build(since, null, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.AuthorizedDate] >= '2026-01-01' ORDER BY [System.Id] ASC");
        /* bypassRules grava ChangedDate no passado; o filtro não pode depender dele. */
        assertThat(wiql).doesNotContain("System.ChangedDate");
    }

    /**
     * System.AuthorizedDate is queried with date precision, so Azure DevOps rejects any
     * literal carrying a time component at all — the bound must be a bare date.
     */
    @Test
    void shouldRenderAuthorizedDateBoundWithoutAnyTimeComponent() {
        Instant since = Instant.parse("2026-08-12T12:34:56.789123Z");

        String wiql = WiqlQueryBuilder.build(since, null, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.AuthorizedDate] >= '2026-08-12' ORDER BY [System.Id] ASC");
        assertThat(wiql).doesNotContain(":");
    }

    /**
     * Truncation must round the bound down to the start of the UTC day; rounding up
     * would silently skip work items changed between midnight and the last sync.
     */
    @Test
    void shouldRoundAuthorizedDateBoundDownToStartOfUtcDay() {
        Instant since = Instant.parse("2026-08-12T23:59:59.999Z");

        String wiql = WiqlQueryBuilder.build(since, null, null);

        assertThat(wiql).contains("[System.AuthorizedDate] >= '2026-08-12'");
    }

    @Test
    void shouldIncludeIdLowerBoundWhenLastSeenIdProvided() {
        String wiql = WiqlQueryBuilder.build(null, 100, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.Id] > 100 ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldCombineDateAndPaginationFiltersWithAnd() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");

        String wiql = WiqlQueryBuilder.build(since, 100, null);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.AuthorizedDate] >= '2026-01-01' AND [System.Id] > 100 ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeWorkItemTypeInClauseWhenTypesConfigured() {
        WiqlFilter filter = new WiqlFilter(List.of("Bug", "User Story"), null, null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] IN ('Bug', 'User Story') ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeAreaPathUnderClauseWhenAreaConfigured() {
        WiqlFilter filter = new WiqlFilter(null, "MyProject\\TeamA", null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.AreaPath] UNDER 'MyProject\\TeamA' ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIncludeIterationPathUnderClauseWhenIterationConfigured() {
        WiqlFilter filter = new WiqlFilter(null, null, "MyProject\\Sprint 1");

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.IterationPath] UNDER 'MyProject\\Sprint 1' ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldCombineAllFilterConditionsWithAnd() {
        Instant since = Instant.parse("2026-01-01T00:00:00Z");
        WiqlFilter filter = new WiqlFilter(List.of("Bug"), "MyProject\\TeamA", "MyProject\\Sprint 1");

        String wiql = WiqlQueryBuilder.build(since, 100, filter);

        assertThat(wiql).isEqualTo(
                "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.AuthorizedDate] >= '2026-01-01'"
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

        assertThat(wiql).isEqualTo("SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldIgnoreBlankAreaAndIterationPaths() {
        WiqlFilter filter = new WiqlFilter(null, "  ", "");

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).isEqualTo("SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project ORDER BY [System.Id] ASC");
    }

    @Test
    void shouldEscapeSingleQuotesInWorkItemTypeAndPaths() {
        WiqlFilter filter = new WiqlFilter(List.of("O'Brien Type"), "Project\\O'Brien", null);

        String wiql = WiqlQueryBuilder.build(null, null, filter);

        assertThat(wiql).contains("'O''Brien Type'");
        assertThat(wiql).contains("UNDER 'Project\\O''Brien'");
    }

    /* Sem a clausula o WIQL da REST abrange a organizacao inteira: um item movido para outro
     * projeto continuaria "presente" e o sync traria itens de outros projetos
     * (azure-itens-excluidos, D2). */
    @Test
    void shouldAlwaysScopeQueryToTheRouteProject() {
        WiqlFilter filter = new WiqlFilter(List.of("Bug"), "MyProject\\TeamA", "MyProject\\Sprint 1");

        assertThat(WiqlQueryBuilder.build(null, null, null)).contains("WHERE [System.TeamProject] = @project");
        assertThat(WiqlQueryBuilder.build(Instant.parse("2026-01-01T00:00:00Z"), 100, filter))
                .startsWith("SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project AND ");
    }
}
