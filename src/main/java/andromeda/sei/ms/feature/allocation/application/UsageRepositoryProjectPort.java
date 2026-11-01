package andromeda.sei.ms.feature.allocation.application;

import java.util.Optional;

/**
 * The project a usage row's repository is bound to, for the approval policy
 * (uso-ia-fora-de-entrega, design 2.1). The adapter delegates to the agent's remote resolver, so the
 * matching rule (GitHub owner/name, Azure Repos name, one binding) lives in one place.
 */
public interface UsageRepositoryProjectPort {

    /** Empty when the remote is blank, unknown, unbound, ambiguous or bound to more than one project. */
    Optional<String> projectIdOf(String organizationId, String repository);
}
