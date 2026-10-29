package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetWorkItemPatternsUseCase {

    private final OrganizationRepository organizationRepository;

    public WorkItemPatterns execute(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getWorkItemPatterns)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }
}
