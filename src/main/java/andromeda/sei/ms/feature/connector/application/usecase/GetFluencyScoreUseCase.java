package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import andromeda.sei.ms.feature.connector.domain.exception.FluencyScoreNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetFluencyScoreUseCase {

    private final FluencyScoreRepository fluencyScoreRepository;

    public FluencyScore execute(String organizationId, String userId) {
        return fluencyScoreRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new FluencyScoreNotFoundException(userId));
    }
}
