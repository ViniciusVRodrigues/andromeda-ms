package andromeda.sei.ms.feature.allocation.infrastructure.attribution;

import andromeda.sei.ms.feature.agent.application.attribution.RemoteRepositoryResolver;
import andromeda.sei.ms.feature.allocation.application.UsageRepositoryProjectPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Delegates to the agent's {@link RemoteRepositoryResolver}: no second copy of the repository-to-project rule. */
@Component
@RequiredArgsConstructor
public class RemoteResolverUsageRepositoryProjectAdapter implements UsageRepositoryProjectPort {

    private final RemoteRepositoryResolver resolver;

    @Override
    public Optional<String> projectIdOf(String organizationId, String repository) {
        if (repository == null || repository.isBlank()) return Optional.empty();
        return resolver.resolveProjectId(organizationId, repository);
    }
}
