package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.WorkItemPatternDetectionUnavailableException;
import org.springframework.stereotype.Component;

/** Placeholder until the detection package (design 14.5): answers 501. */
@Component
public class DetectWorkItemPatternsUseCase {

    public WorkItemPatterns execute(String organizationId) {
        throw new WorkItemPatternDetectionUnavailableException();
    }
}
