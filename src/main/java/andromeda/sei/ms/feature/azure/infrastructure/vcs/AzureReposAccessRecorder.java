package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Keeps {@code azure_connections.reposAccess} in line with what the Git endpoints just answered
 * (design D6: computed at connection time, re-evaluated when listing repositories). Best effort:
 * writes only when the value changes, and never fails the caller (a concurrent save of the
 * connection simply wins).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureReposAccessRecorder {

    private final AzureConnectionRepository azureConnectionRepository;

    public void record(String organizationId, boolean reposAccess) {
        try {
            azureConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                    .filter(connection -> !Objects.equals(connection.getReposAccess(), reposAccess))
                    .ifPresent(connection -> {
                        connection.setReposAccess(reposAccess);
                        azureConnectionRepository.save(connection);
                    });
        } catch (RuntimeException e) {
            log.warn("Could not record reposAccess={} for org={}: {}", reposAccess, organizationId, e.getMessage());
        }
    }
}
