package andromeda.sei.ms.feature.github.domain;

import java.util.List;
import java.util.Optional;

public interface GithubConnectionRepository {

    Optional<GithubConnection> findByOrganizationIdAndActiveTrue(String organizationId);

    List<GithubConnection> findAllByStatusAndActiveTrue(GithubConnectionStatus status);

    GithubConnection save(GithubConnection connection);
}
