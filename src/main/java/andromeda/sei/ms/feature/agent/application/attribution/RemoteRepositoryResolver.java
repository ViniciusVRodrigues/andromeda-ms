package andromeda.sei.ms.feature.agent.application.attribution;

import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Git remote (any common form) to the project the repository is bound to (design 5.2). */
@Component
@RequiredArgsConstructor
public class RemoteRepositoryResolver {

    private final RepositoryProjectPort repositoryProjectPort;

    public Optional<String> resolveProjectId(String organizationId, String remote) {
        if (organizationId == null) return Optional.empty();
        return RemoteRepositoryRef.parse(remote)
                .flatMap(ref -> repositoryProjectPort.findProjectId(organizationId, ref));
    }
}
