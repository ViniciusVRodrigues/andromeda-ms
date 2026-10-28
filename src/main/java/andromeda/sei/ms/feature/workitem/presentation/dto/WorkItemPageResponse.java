package andromeda.sei.ms.feature.workitem.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;

import java.util.List;
import java.util.Map;

public record WorkItemPageResponse(
        int page,
        int size,
        long totalElements,
        List<WorkItemSummaryResponse> content,
        /** Frescura dos dados do projeto (on-demand-refresh); {@code null} fora do backlog de projeto. */
        DataFreshness dataFreshness,
        /** Níveis do resultado inteiro (todas as páginas); só nas raízes, omitido nos órfãos. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<LevelResponse> levels
) {
    public record LevelResponse(Integer rank, List<String> rawTypes, long count) {
    }

    public WorkItemPageResponse withDataFreshness(DataFreshness freshness) {
        return new WorkItemPageResponse(page, size, totalElements, content, freshness, levels);
    }

    /** Versão para as raízes: acrescenta os níveis do resultado inteiro. */
    public WorkItemPageResponse withLevels(WorkItemPageResult result) {
        return new WorkItemPageResponse(page, size, totalElements, content, dataFreshness,
                result.getLevels().stream()
                        .map(l -> new LevelResponse(l.rank(), l.rawTypes(), l.count()))
                        .toList());
    }

    public static WorkItemPageResponse from(WorkItemPageResult result) {
        return from(result, Map.of());
    }

    /**
     * @param assigneeNamesByUserId nomes dos responsáveis já resolvidos, por id de usuário. É um
     *                              mapa (e não um lookup por item) para que o chamador faça uma
     *                              única consulta de usuários por requisição, em vez de uma por
     *                              linha da página.
     */
    public static WorkItemPageResponse from(WorkItemPageResult result,
                                             Map<String, String> assigneeNamesByUserId) {
        return new WorkItemPageResponse(
                result.getPage(),
                result.getSize(),
                result.getTotalElements(),
                result.getContent().stream()
                        .map(item -> WorkItemSummaryResponse.from(
                                item,
                                result.metricsFor(item.getId()),
                                item.getAssigneeUserId() == null
                                        ? null
                                        : assigneeNamesByUserId.get(item.getAssigneeUserId()),
                                result.infoFor(item.getId())))
                        .toList(),
                null,
                null);
    }
}
