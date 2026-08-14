package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.Locale;

/**
 * Critérios opcionais de filtragem do backlog. Todos os campos são {@code null}-tolerantes:
 * ausente significa "não restringe".
 *
 * <p>A filtragem acontece em memória, sobre o conjunto que
 * {@link ProjectBacklogClassificationService} já carrega por inteiro para poder classificar a
 * hierarquia. Isso é deliberado, não uma limitação: empurrar estes predicados para a query do
 * Mongo obrigaria a paginar antes de classificar, que é exatamente o bug de páginas curtas e
 * {@code totalElements} mentiroso que aquele serviço existe para evitar. Filtrar aqui — antes de
 * paginar — mantém a invariante de que o total corresponde ao que é devolvido.
 *
 * <p>Por consequência, {@code search} pode ser {@code contains} case-insensitive sem custo de
 * índice: não há query envolvida, então a limitação usual de {@code $regex} não-ancorado não se
 * aplica.
 */
public record BacklogFilter(String sprintId, String assigneeUserId, String search,
                             WorkItem.ResolvedType resolvedType, WorkItem.ResolvedStatus resolvedStatus) {

    public static final BacklogFilter NONE = new BacklogFilter(null, null, null, null, null);

    public static BacklogFilter of(String sprintId, String assigneeUserId, String search) {
        return of(sprintId, assigneeUserId, search, null, null);
    }

    /**
     * Tipo e status usam os valores <em>resolvidos</em> (STORY/BUG/TASK…, TODO/IN_PROGRESS/DONE)
     * e não os brutos do Azure: são eles que a UI exibe nos badges, e são estáveis entre
     * organizações que nomeiam seus tipos e estados de forma diferente.
     */
    public static BacklogFilter of(String sprintId, String assigneeUserId, String search,
                                    WorkItem.ResolvedType resolvedType,
                                    WorkItem.ResolvedStatus resolvedStatus) {
        return new BacklogFilter(blankToNull(sprintId), blankToNull(assigneeUserId), blankToNull(search),
                resolvedType, resolvedStatus);
    }

    /**
     * {@code true} quando nenhum critério foi informado — a UI usa isto para decidir entre a
     * visão em árvore (sem filtro) e a lista plana (com filtro).
     */
    public boolean isEmpty() {
        return sprintId == null && assigneeUserId == null && search == null
                && resolvedType == null && resolvedStatus == null;
    }

    public boolean matches(WorkItem item) {
        if (sprintId != null && !sprintId.equals(item.getSprintId())) return false;
        if (assigneeUserId != null && !assigneeUserId.equals(item.getAssigneeUserId())) return false;
        if (resolvedType != null && resolvedType != item.getResolvedType()) return false;
        if (resolvedStatus != null && resolvedStatus != item.getResolvedStatus()) return false;
        if (search != null && !matchesSearch(item)) return false;
        return true;
    }

    /**
     * Busca casa por id externo (o número que a pessoa vê no Azure e digita para localizar) ou
     * por trecho do título. O id é comparado por igualdade, não por {@code contains}: buscar
     * "12" não deve trazer todo item cujo número contenha 12.
     */
    private boolean matchesSearch(WorkItem item) {
        String needle = search.toLowerCase(Locale.ROOT);
        if (item.getExternalId() != null && item.getExternalId().equalsIgnoreCase(search)) return true;
        return item.getTitle() != null && item.getTitle().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
