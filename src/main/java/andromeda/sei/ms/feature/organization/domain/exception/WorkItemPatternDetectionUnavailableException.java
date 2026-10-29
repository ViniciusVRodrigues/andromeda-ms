package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** 501 {@code WORK_ITEM_PATTERN_DETECTION_UNAVAILABLE}: the automatic detection arrives in a later package. */
public class WorkItemPatternDetectionUnavailableException extends DomainException {

    public WorkItemPatternDetectionUnavailableException() {
        super("WORK_ITEM_PATTERN_DETECTION_UNAVAILABLE", "Automatic work item pattern detection is not available yet");
    }
}
