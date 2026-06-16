package andromeda.sei.ms.feature.connector.application.port;

import andromeda.sei.ms.feature.connector.domain.FluencyScore;

import java.util.Optional;

public interface FluencyScoreRepository {

    void upsertByUserAndOrg(FluencyScore score);

    Optional<FluencyScore> findByOrganizationIdAndUserId(String organizationId, String userId);
}
