package andromeda.sei.ms.feature.connector.presentation.dto;

import java.util.List;

/**
 * @param overallStage {@code null} quando o usuário ainda não tem score calculado —
 *                     daí {@link Integer} em vez de {@code int}. Ver {@link #empty()}.
 */
public record FluencyScoreResponse(
        Integer overallStage,
        String overallLabel,
        String computedAt,
        List<CategoryScoreResponse> categories
) {
    /**
     * Resposta para um usuário sem score calculado. {@code categories} vazia (e não
     * nula) é o que a UI usa para distinguir "ainda não há dados" de "falha ao
     * carregar", então o contrato garante a lista sempre presente.
     */
    public static FluencyScoreResponse empty() {
        return new FluencyScoreResponse(null, null, null, List.of());
    }
}
