package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationDeleteConfirmationMismatchException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.shared.application.security.OrganizationDeactivated;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Deletion of the caller's organization: cuts access at once (inactive + tombstone with
 * {@code deletedAt}/{@code deletedBy}, event published, 204) and then triggers the background
 * {@link PurgeOrganizationDataUseCase}, which erases the organization's data for good.
 */
@Component
@RequiredArgsConstructor
public class DeleteOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PurgeOrganizationDataUseCase purgeUseCase;

    public void execute(String organizationId, String deletedBy, String confirmSlug) {
        var organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        if (!organization.getSlug().equals(confirmSlug)) {
            throw new OrganizationDeleteConfirmationMismatchException();
        }

        Instant now = Instant.now();
        organization.setActive(false);
        organization.setDeletedAt(now);
        organization.setDeletedBy(deletedBy);
        organization.setUpdatedAt(now);
        organizationRepository.save(organization);
        eventPublisher.publishEvent(new OrganizationDeactivated(organizationId));
        purgeUseCase.execute(organizationId);
    }
}
