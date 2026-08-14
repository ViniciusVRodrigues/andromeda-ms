package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetFluencyScoreUseCase {

    private final FluencyScoreRepository fluencyScoreRepository;

    /**
     * Devolve {@link Optional#empty()} — e não lança — quando o usuário ainda não tem
     * score calculado. Não ter score é o estado inicial de todo usuário novo, não uma
     * condição excepcional: modelá-lo como 404 fazia a UI exibir "erro ao carregar" no
     * lugar do empty state. Os demais endpoints de IA (usage/summary, sprint-items,
     * team-members) já respondem 200 com conteúdo vazio; este era o desviante.
     */
    public Optional<FluencyScore> execute(String organizationId, String userId) {
        return fluencyScoreRepository.findByOrganizationIdAndUserId(organizationId, userId);
    }
}
