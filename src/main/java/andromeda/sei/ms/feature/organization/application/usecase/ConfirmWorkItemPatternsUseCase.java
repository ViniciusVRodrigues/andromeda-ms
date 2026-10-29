package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Keeps the current patterns (typically the detected ones) and turns the mode into CUSTOM, with {@code confirmedBy}. */
@Component
@RequiredArgsConstructor
public class ConfirmWorkItemPatternsUseCase {

    private final OrganizationRepository organizationRepository;

    public WorkItemPatterns execute(String organizationId, String userId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        WorkItemPatterns c = organization.getWorkItemPatterns();
        Instant now = Instant.now();
        organization.setWorkItemPatterns(new WorkItemPatterns(c.enabled(), WorkItemPatterns.Mode.CUSTOM, c.patterns(),
                userId, now, userId, now, c.detection()));
        return organizationRepository.save(organization).getWorkItemPatterns();
    }
}
