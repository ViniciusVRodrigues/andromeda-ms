package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import andromeda.sei.ms.feature.connector.domain.FluencyScoreCategory;
import andromeda.sei.ms.feature.connector.presentation.dto.FluencyScoreRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreFluencyScoreUseCase {

    private final FluencyScoreRepository fluencyScoreRepository;

    public void execute(FluencyScoreRequest request, String organizationId, String userId) {
        Instant now = Instant.now();

        List<FluencyScoreCategory> categories = request.categories() == null ? List.of() :
                request.categories().stream()
                        .map(c -> new FluencyScoreCategory(c.category(), c.icon(), c.stage(), c.tips()))
                        .toList();

        FluencyScore score = new FluencyScore(
                null,
                organizationId,
                userId,
                request.overallStage(),
                request.overallLabel(),
                request.computedAt(),
                categories,
                true,
                null,
                now,
                now
        );

        fluencyScoreRepository.upsertByUserAndOrg(score);
    }
}
