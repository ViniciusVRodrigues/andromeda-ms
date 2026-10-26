package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationDeleteConfirmationMismatchException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.shared.application.security.OrganizationDeactivated;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Logical deletion of the caller's organization (soft delete); users and data are left untouched. */
@Component
@RequiredArgsConstructor
public class DeleteOrganizationUseCase {

    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(String organizationId, String confirmSlug) {
        var organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));

        if (!organization.getSlug().equals(confirmSlug)) {
            throw new OrganizationDeleteConfirmationMismatchException();
        }

        organization.setActive(false);
        organization.setUpdatedAt(Instant.now());
        organizationRepository.save(organization);
        eventPublisher.publishEvent(new OrganizationDeactivated(organizationId));
    }
}
