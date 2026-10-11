package andromeda.sei.ms.feature.connector.domain;

import java.util.List;
import java.util.Optional;

public interface ConnectorTokenRepository {

    Optional<ConnectorToken> findByIdAndOrganizationId(String id, String organizationId);

    Optional<ConnectorToken> findByOrganizationIdAndTokenHash(String organizationId, String tokenHash);

    List<ConnectorToken> findActiveByOrganizationIdAndUserId(String organizationId, String userId);

    /** Every token of the organization, active and revoked (the dashboards count both). */
    List<ConnectorToken> findAllByOrganizationId(String organizationId);

    ConnectorToken save(ConnectorToken token);
}
