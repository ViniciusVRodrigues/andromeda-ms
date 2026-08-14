package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BacklogFilterTest {

    private static final Instant NOW = Instant.now();

    @Test
    void noneShouldBeEmpty() {
        assertThat(BacklogFilter.NONE.isEmpty()).isTrue();
    }

    /** Strings em branco vindas de query params são "sem filtro", não filtro por vazio. */
    @Test
    void shouldTreatBlankParamsAsAbsent() {
        BacklogFilter filter = BacklogFilter.of("", "   ", "");

        assertThat(filter.isEmpty()).isTrue();
        assertThat(filter.sprintId()).isNull();
        assertThat(filter.assigneeUserId()).isNull();
        assertThat(filter.search()).isNull();
    }

    @Test
    void emptyFilterShouldMatchEverything() {
        assertThat(BacklogFilter.NONE.matches(item("1", "Qualquer", "sprint-1", "user-1"))).isTrue();
    }

    @Test
    void shouldMatchOnSprint() {
        BacklogFilter filter = BacklogFilter.of("sprint-1", null, null);

        assertThat(filter.matches(item("1", "T", "sprint-1", null))).isTrue();
        assertThat(filter.matches(item("2", "T", "sprint-2", null))).isFalse();
        assertThat(filter.matches(item("3", "T", null, null))).isFalse();
    }

    @Test
    void shouldMatchOnAssignee() {
        BacklogFilter filter = BacklogFilter.of(null, "user-1", null);

        assertThat(filter.matches(item("1", "T", null, "user-1"))).isTrue();
        assertThat(filter.matches(item("2", "T", null, "user-2"))).isFalse();
        assertThat(filter.matches(item("3", "T", null, null))).isFalse();
    }

    @Test
    void searchShouldMatchTitleAsCaseInsensitiveSubstring() {
        BacklogFilter filter = BacklogFilter.of(null, null, "LOGIN");

        assertThat(filter.matches(item("1", "Tela de login do app", null, null))).isTrue();
        assertThat(filter.matches(item("2", "LOGIN", null, null))).isTrue();
        assertThat(filter.matches(item("3", "Cadastro", null, null))).isFalse();
    }

    @Test
    void searchShouldMatchExternalId() {
        BacklogFilter filter = BacklogFilter.of(null, null, "1234");

        assertThat(filter.matches(item("1234", "Qualquer título", null, null))).isTrue();
    }

    /**
     * Id casa por igualdade, não por substring: buscar "12" traria todo item cujo número
     * contivesse 12, o que torna a busca por id inútil em backlogs grandes.
     */
    @Test
    void searchShouldNotMatchExternalIdPartially() {
        BacklogFilter filter = BacklogFilter.of(null, null, "12");

        assertThat(filter.matches(item("1234", "Sem correspondência no título", null, null))).isFalse();
    }

    @Test
    void shouldMatchOnResolvedType() {
        BacklogFilter filter = BacklogFilter.of(null, null, null, WorkItem.ResolvedType.BUG, null);

        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.TODO))).isTrue();
        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.TODO))).isFalse();
    }

    @Test
    void shouldMatchOnResolvedStatus() {
        BacklogFilter filter = BacklogFilter.of(null, null, null, null, WorkItem.ResolvedStatus.DONE);

        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.DONE))).isTrue();
        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.TASK, WorkItem.ResolvedStatus.TODO))).isFalse();
    }

    @Test
    void typeAndStatusShouldCombineWithAnd() {
        BacklogFilter filter = BacklogFilter.of(null, null, null,
                WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.DONE);

        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.DONE))).isTrue();
        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.TODO))).isFalse();
        assertThat(filter.matches(typedItem(WorkItem.ResolvedType.STORY, WorkItem.ResolvedStatus.DONE))).isFalse();
    }

    @Test
    void filterWithOnlyTypeOrStatusShouldNotBeEmpty() {
        assertThat(BacklogFilter.of(null, null, null, WorkItem.ResolvedType.BUG, null).isEmpty()).isFalse();
        assertThat(BacklogFilter.of(null, null, null, null, WorkItem.ResolvedStatus.DONE).isEmpty()).isFalse();
    }

    @Test
    void multipleCriteriaShouldCombineWithAnd() {
        BacklogFilter filter = BacklogFilter.of("sprint-1", "user-1", "login");

        assertThat(filter.matches(item("1", "Tela de login", "sprint-1", "user-1"))).isTrue();
        assertThat(filter.matches(item("2", "Tela de login", "sprint-2", "user-1"))).isFalse();
        assertThat(filter.matches(item("3", "Cadastro", "sprint-1", "user-1"))).isFalse();
    }

    @Test
    void shouldNotBreakOnItemsWithNullTitleOrExternalId() {
        BacklogFilter filter = BacklogFilter.of(null, null, "x");

        assertThat(filter.matches(item(null, null, null, null))).isFalse();
    }

    private WorkItem typedItem(WorkItem.ResolvedType type, WorkItem.ResolvedStatus status) {
        return new WorkItem("id-1", "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "1", NOW, "Título", "Task", type, "Active", status,
                null, null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }

    private WorkItem item(String externalId, String title, String sprintId, String assigneeUserId) {
        return new WorkItem("id-" + externalId, "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, title, "Task", WorkItem.ResolvedType.TASK,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), sprintId, null, assigneeUserId, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }
}
